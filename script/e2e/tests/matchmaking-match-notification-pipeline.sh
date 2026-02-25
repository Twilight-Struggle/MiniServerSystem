#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/matchmaking-match-notification-pipeline.sh
# What: Verify matchmaking match result reaches notification persistence.
# Why: Detect regressions on MatchmakerWorker -> NATS -> Notification pipeline.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
REALM="miniserversystem"
USERNAME="test"
PASSWORD="test"
KEYCLOAK_ADMIN_USERNAME="admin"
KEYCLOAK_ADMIN_PASSWORD="admin"
NAMESPACE="miniserversystem"
POSTGRES_STATEFULSET="postgres"
POSTGRES_CONTAINER="postgres"
POSTGRES_DB="miniserversystem"
POSTGRES_USER="miniserversystem"
POSTGRES_PASSWORD="miniserversystem"
MODE="casual"
TIMEOUT_SEC="120"
POLL_INTERVAL_SEC="2"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>                 Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url>        Keycloak base URL (example: http://keycloak.localhost:18081)
  --realm <name>                   Keycloak realm name (default: ${REALM})
  --username <name>                Primary user username (default: ${USERNAME})
  --password <pw>                  Primary user password (default: ${PASSWORD})
  --keycloak-admin-username <name> Keycloak admin username (default: ${KEYCLOAK_ADMIN_USERNAME})
  --keycloak-admin-password <pw>   Keycloak admin password (default: ${KEYCLOAK_ADMIN_PASSWORD})
  --namespace <ns>                 Kubernetes namespace (default: ${NAMESPACE})
  --postgres-statefulset <name>    Postgres StatefulSet name (default: ${POSTGRES_STATEFULSET})
  --postgres-container <name>      Postgres container name (default: ${POSTGRES_CONTAINER})
  --postgres-db <name>             Postgres DB name (default: ${POSTGRES_DB})
  --postgres-user <name>           Postgres user (default: ${POSTGRES_USER})
  --postgres-password <pw>         Postgres password (default: ${POSTGRES_PASSWORD})
  --mode <mode>                    Matchmaking mode (default: ${MODE})
  --timeout-sec <sec>              Poll timeout in seconds (default: ${TIMEOUT_SEC})
  --poll-interval-sec <sec>        Poll interval in seconds (default: ${POLL_INTERVAL_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-base-url) KEYCLOAK_BASE_URL="$2"; shift 2 ;;
    --realm) REALM="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --keycloak-admin-username) KEYCLOAK_ADMIN_USERNAME="$2"; shift 2 ;;
    --keycloak-admin-password) KEYCLOAK_ADMIN_PASSWORD="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --postgres-statefulset) POSTGRES_STATEFULSET="$2"; shift 2 ;;
    --postgres-container) POSTGRES_CONTAINER="$2"; shift 2 ;;
    --postgres-db) POSTGRES_DB="$2"; shift 2 ;;
    --postgres-user) POSTGRES_USER="$2"; shift 2 ;;
    --postgres-password) POSTGRES_PASSWORD="$2"; shift 2 ;;
    --mode) MODE="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    --poll-interval-sec) POLL_INTERVAL_SEC="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "${BASE_URL}" || -z "${KEYCLOAK_BASE_URL}" ]]; then
  usage
  exit 2
fi

require_cmd curl
require_cmd perl
require_cmd kubectl

KEYCLOAK_AUTHORITY="${KEYCLOAK_BASE_URL#http://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY#https://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY%%/*}"
KEYCLOAK_HOST="${KEYCLOAK_AUTHORITY%%:*}"
KEYCLOAK_PORT="${KEYCLOAK_AUTHORITY##*:}"
KEYCLOAK_RESOLVE_ARGS=()
if [[ "${KEYCLOAK_HOST}" != "127.0.0.1" && "${KEYCLOAK_HOST}" != "localhost" ]]; then
  KEYCLOAK_RESOLVE_ARGS=(--resolve "${KEYCLOAK_HOST}:${KEYCLOAK_PORT}:127.0.0.1")
fi

COOKIE_USER1="$(mktemp)"
COOKIE_USER2="$(mktemp)"
ADMIN_TOKEN_BODY="$(mktemp)"
CREATE_USER_BODY="$(mktemp)"
CREATE_USER_HEADERS="$(mktemp)"
RESET_PASSWORD_BODY="$(mktemp)"
JOIN_BODY_1="$(mktemp)"
JOIN_BODY_2="$(mktemp)"
STATUS_BODY_1="$(mktemp)"
STATUS_BODY_2="$(mktemp)"
cleanup() {
  rm -f \
    "${COOKIE_USER1}" "${COOKIE_USER2}" \
    "${ADMIN_TOKEN_BODY}" "${CREATE_USER_BODY}" "${CREATE_USER_HEADERS}" "${RESET_PASSWORD_BODY}" \
    "${JOIN_BODY_1}" "${JOIN_BODY_2}" "${STATUS_BODY_1}" "${STATUS_BODY_2}" || true
}
trap cleanup EXIT

query_postgres() {
  local sql="$1"
  kubectl -n "${NAMESPACE}" exec "statefulset/${POSTGRES_STATEFULSET}" -c "${POSTGRES_CONTAINER}" -- \
    env PGPASSWORD="${POSTGRES_PASSWORD}" \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "${sql}" | tr -d '\r'
}

extract_match_id_from_status_json() {
  perl -0777 -ne 'if (/"matched"\s*:\s*\{[^}]*"match_id"\s*:\s*"([^"]+)"/s) { print $1; }'
}

request_keycloak_admin_token() {
  local token_url="${KEYCLOAK_BASE_URL}/realms/master/protocol/openid-connect/token"
  local code
  code="$(
    curl -sS -o "${ADMIN_TOKEN_BODY}" -w "%{http_code}" \
      "${KEYCLOAK_RESOLVE_ARGS[@]}" \
      -X POST "${token_url}" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=password" \
      --data-urlencode "client_id=admin-cli" \
      --data-urlencode "username=${KEYCLOAK_ADMIN_USERNAME}" \
      --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD}"
  )"
  if [[ "${code}" != "200" ]]; then
    echo "ERROR: failed to get Keycloak admin token (status=${code})" >&2
    echo "response=$(cat "${ADMIN_TOKEN_BODY}")" >&2
    return 1
  fi
  extract_json_field "$(cat "${ADMIN_TOKEN_BODY}")" "access_token"
}

create_ephemeral_user() {
  local admin_token="$1"
  local username="$2"
  local password="$3"
  local payload
  payload="$(cat <<EOF
{"username":"${username}","enabled":true,"emailVerified":true}
EOF
)"
  local code
  code="$(
    curl -sS -o "${CREATE_USER_BODY}" -D "${CREATE_USER_HEADERS}" -w "%{http_code}" \
      "${KEYCLOAK_RESOLVE_ARGS[@]}" \
      -X POST "${KEYCLOAK_BASE_URL}/admin/realms/${REALM}/users" \
      -H "Authorization: Bearer ${admin_token}" \
      -H "Content-Type: application/json" \
      -d "${payload}"
  )"
  if [[ "${code}" != "201" ]]; then
    echo "ERROR: failed to create Keycloak user (status=${code})" >&2
    echo "response=$(cat "${CREATE_USER_BODY}")" >&2
    return 1
  fi

  local user_location
  local user_id
  user_location="$(extract_header_value "${CREATE_USER_HEADERS}" "Location")"
  user_id="$(printf '%s' "${user_location}" | sed -n 's#.*/users/\([^/?]*\).*#\1#p')"
  if [[ -z "${user_id}" ]]; then
    echo "ERROR: failed to parse created Keycloak user id from Location header" >&2
    echo "location=${user_location}" >&2
    return 1
  fi

  local reset_payload
  reset_payload="$(cat <<EOF
{"type":"password","value":"${password}","temporary":false}
EOF
)"
  local reset_code
  reset_code="$(
    curl -sS -o "${RESET_PASSWORD_BODY}" -w "%{http_code}" \
      "${KEYCLOAK_RESOLVE_ARGS[@]}" \
      -X PUT "${KEYCLOAK_BASE_URL}/admin/realms/${REALM}/users/${user_id}/reset-password" \
      -H "Authorization: Bearer ${admin_token}" \
      -H "Content-Type: application/json" \
      -d "${reset_payload}"
  )"
  if [[ "${reset_code}" != "204" ]]; then
    echo "ERROR: failed to reset Keycloak user password (status=${reset_code})" >&2
    echo "response=$(cat "${RESET_PASSWORD_BODY}")" >&2
    return 1
  fi
}

join_ticket() {
  local cookie_jar="$1"
  local idempotency_key="$2"
  local output_file="$3"
  local body
  body="$(cat <<EOF
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${idempotency_key}"}
EOF
)"
  curl -sS -o "${output_file}" -w "%{http_code}" \
    -c "${cookie_jar}" -b "${cookie_jar}" \
    -X POST "${BASE_URL}/v1/matchmaking/queues/${MODE}/tickets" \
    -H "Content-Type: application/json" \
    -d "${body}"
}

get_status() {
  local cookie_jar="$1"
  local ticket_id="$2"
  local output_file="$3"
  curl -sS -o "${output_file}" -w "%{http_code}" \
    -c "${cookie_jar}" -b "${cookie_jar}" \
    "${BASE_URL}/v1/matchmaking/tickets/${ticket_id}"
}

echo "=== OIDC login for primary user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_USER1}"

echo "=== Creating and logging in secondary user ==="
ADMIN_TOKEN="$(request_keycloak_admin_token)"
if [[ -z "${ADMIN_TOKEN}" ]]; then
  echo "ERROR: admin access_token is empty" >&2
  exit 1
fi
SECONDARY_USERNAME="e2e-mm-pair-$(date +%s)-${RANDOM}"
SECONDARY_PASSWORD="test"
create_ephemeral_user "${ADMIN_TOKEN}" "${SECONDARY_USERNAME}" "${SECONDARY_PASSWORD}"
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${SECONDARY_USERNAME}" "${SECONDARY_PASSWORD}" "${COOKIE_USER2}"

IDEMPOTENCY_KEY_1="e2e-mm-match-1-$(date +%s)-${RANDOM}"
IDEMPOTENCY_KEY_2="e2e-mm-match-2-$(date +%s)-${RANDOM}"

echo "=== Join matchmaking queue with two users ==="
JOIN_CODE_1="$(join_ticket "${COOKIE_USER1}" "${IDEMPOTENCY_KEY_1}" "${JOIN_BODY_1}")"
JOIN_CODE_2="$(join_ticket "${COOKIE_USER2}" "${IDEMPOTENCY_KEY_2}" "${JOIN_BODY_2}")"
if [[ "${JOIN_CODE_1}" != "200" || "${JOIN_CODE_2}" != "200" ]]; then
  echo "ERROR: join failed (user1=${JOIN_CODE_1}, user2=${JOIN_CODE_2})" >&2
  echo "join1=$(cat "${JOIN_BODY_1}")" >&2
  echo "join2=$(cat "${JOIN_BODY_2}")" >&2
  exit 1
fi

TICKET_ID_1="$(extract_json_field "$(cat "${JOIN_BODY_1}")" "ticket_id")"
TICKET_ID_2="$(extract_json_field "$(cat "${JOIN_BODY_2}")" "ticket_id")"
if [[ -z "${TICKET_ID_1}" || -z "${TICKET_ID_2}" ]]; then
  echo "ERROR: ticket_id is missing" >&2
  echo "join1=$(cat "${JOIN_BODY_1}")" >&2
  echo "join2=$(cat "${JOIN_BODY_2}")" >&2
  exit 1
fi

echo "=== Poll until both tickets are MATCHED ==="
MATCH_ID_1=""
MATCH_ID_2=""
END=$((SECONDS + TIMEOUT_SEC))
while [[ ${SECONDS} -lt ${END} ]]; do
  STATUS_CODE_1="$(get_status "${COOKIE_USER1}" "${TICKET_ID_1}" "${STATUS_BODY_1}")"
  STATUS_CODE_2="$(get_status "${COOKIE_USER2}" "${TICKET_ID_2}" "${STATUS_BODY_2}")"
  if [[ "${STATUS_CODE_1}" != "200" || "${STATUS_CODE_2}" != "200" ]]; then
    echo "ERROR: status check failed (user1=${STATUS_CODE_1}, user2=${STATUS_CODE_2})" >&2
    echo "status1=$(cat "${STATUS_BODY_1}")" >&2
    echo "status2=$(cat "${STATUS_BODY_2}")" >&2
    exit 1
  fi
  STATUS_1="$(extract_json_field "$(cat "${STATUS_BODY_1}")" "status")"
  STATUS_2="$(extract_json_field "$(cat "${STATUS_BODY_2}")" "status")"
  if [[ "${STATUS_1}" == "MATCHED" && "${STATUS_2}" == "MATCHED" ]]; then
    MATCH_ID_1="$(cat "${STATUS_BODY_1}" | extract_match_id_from_status_json)"
    MATCH_ID_2="$(cat "${STATUS_BODY_2}" | extract_match_id_from_status_json)"
    if [[ -n "${MATCH_ID_1}" && "${MATCH_ID_1}" == "${MATCH_ID_2}" ]]; then
      break
    fi
  fi
  sleep "${POLL_INTERVAL_SEC}"
done

if [[ -z "${MATCH_ID_1}" || -z "${MATCH_ID_2}" || "${MATCH_ID_1}" != "${MATCH_ID_2}" ]]; then
  echo "ERROR: tickets did not converge to the same match_id within timeout" >&2
  echo "ticket1=${TICKET_ID_1} body=$(cat "${STATUS_BODY_1}")" >&2
  echo "ticket2=${TICKET_ID_2} body=$(cat "${STATUS_BODY_2}")" >&2
  exit 1
fi
MATCH_ID="${MATCH_ID_1}"
echo "Matched with match_id=${MATCH_ID}"

echo "=== Verify Notification persistence for MatchFound ==="
EVENT_ID=""
PROCESSED_EXISTS="f"
END=$((SECONDS + TIMEOUT_SEC))
while [[ ${SECONDS} -lt ${END} ]]; do
  EVENT_ID="$(
    query_postgres "SELECT event_id::text FROM notification.notifications WHERE type = 'MatchFound' AND payload::text LIKE '%\"match_id\":\"${MATCH_ID}\"%' ORDER BY created_at DESC LIMIT 1;" \
      | head -n1 | tr -d '[:space:]'
  )"
  if [[ -n "${EVENT_ID}" ]]; then
    PROCESSED_EXISTS="$(
      query_postgres "SELECT EXISTS (SELECT 1 FROM notification.processed_events WHERE event_id = '${EVENT_ID}'::uuid);" \
        | tr -d '[:space:]'
    )"
    if [[ "${PROCESSED_EXISTS}" == "t" ]]; then
      break
    fi
  fi
  sleep "${POLL_INTERVAL_SEC}"
done

if [[ -z "${EVENT_ID}" ]]; then
  echo "ERROR: MatchFound notification not found for match_id=${MATCH_ID}" >&2
  query_postgres "SELECT notification_id::text, event_id::text, type, payload, status FROM notification.notifications WHERE type='MatchFound' ORDER BY created_at DESC LIMIT 20;"
  exit 1
fi

if [[ "${PROCESSED_EXISTS}" != "t" ]]; then
  echo "ERROR: processed_events missing event_id=${EVENT_ID}" >&2
  query_postgres "SELECT event_id::text, processed_at FROM notification.processed_events WHERE event_id='${EVENT_ID}'::uuid;"
  exit 1
fi

echo "=== matchmaking-match-notification-pipeline test passed (match_id=${MATCH_ID}, event_id=${EVENT_ID}) ==="
