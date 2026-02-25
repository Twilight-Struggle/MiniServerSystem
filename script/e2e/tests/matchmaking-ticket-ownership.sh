#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/matchmaking-ticket-ownership.sh
# What: Verify non-owner cannot read/cancel another user's matchmaking ticket.
# Why: Prevent horizontal privilege escalation on ticket resource.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
REALM="miniserversystem"
USERNAME="test"
PASSWORD="test"
KEYCLOAK_ADMIN_USERNAME="admin"
KEYCLOAK_ADMIN_PASSWORD="admin"
MODE="casual"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>                 Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url>        Keycloak base URL (example: http://keycloak.localhost:18081)
  --realm <name>                   Keycloak realm name (default: ${REALM})
  --username <name>                Owner user username (default: ${USERNAME})
  --password <pw>                  Owner user password (default: ${PASSWORD})
  --keycloak-admin-username <name> Keycloak admin username (default: ${KEYCLOAK_ADMIN_USERNAME})
  --keycloak-admin-password <pw>   Keycloak admin password (default: ${KEYCLOAK_ADMIN_PASSWORD})
  --mode <mode>                    Matchmaking mode (default: ${MODE})
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
    --mode) MODE="$2"; shift 2 ;;
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

KEYCLOAK_AUTHORITY="${KEYCLOAK_BASE_URL#http://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY#https://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY%%/*}"
KEYCLOAK_HOST="${KEYCLOAK_AUTHORITY%%:*}"
KEYCLOAK_PORT="${KEYCLOAK_AUTHORITY##*:}"
KEYCLOAK_RESOLVE_ARGS=()
if [[ "${KEYCLOAK_HOST}" != "127.0.0.1" && "${KEYCLOAK_HOST}" != "localhost" ]]; then
  KEYCLOAK_RESOLVE_ARGS=(--resolve "${KEYCLOAK_HOST}:${KEYCLOAK_PORT}:127.0.0.1")
fi

COOKIE_OWNER="$(mktemp)"
COOKIE_OTHER="$(mktemp)"
JOIN_BODY="$(mktemp)"
GET_BODY="$(mktemp)"
DELETE_BODY="$(mktemp)"
ADMIN_TOKEN_BODY="$(mktemp)"
CREATE_USER_BODY="$(mktemp)"
CREATE_USER_HEADERS="$(mktemp)"
RESET_PASSWORD_BODY="$(mktemp)"
cleanup() {
  rm -f \
    "${COOKIE_OWNER}" "${COOKIE_OTHER}" "${JOIN_BODY}" "${GET_BODY}" "${DELETE_BODY}" \
    "${ADMIN_TOKEN_BODY}" "${CREATE_USER_BODY}" "${CREATE_USER_HEADERS}" "${RESET_PASSWORD_BODY}" || true
}
trap cleanup EXIT

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

echo "=== OIDC login for owner user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_OWNER}"

echo "=== Creating secondary Keycloak user for ownership test ==="
ADMIN_TOKEN="$(request_keycloak_admin_token)"
if [[ -z "${ADMIN_TOKEN}" ]]; then
  echo "ERROR: admin access_token is empty" >&2
  exit 1
fi
OTHER_USERNAME="e2e-mm-other-$(date +%s)-${RANDOM}"
OTHER_PASSWORD="test"
create_ephemeral_user "${ADMIN_TOKEN}" "${OTHER_USERNAME}" "${OTHER_PASSWORD}"

echo "=== OIDC login for secondary user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${OTHER_USERNAME}" "${OTHER_PASSWORD}" "${COOKIE_OTHER}"

IDEMPOTENCY_KEY="e2e-mm-owner-$(date +%s)-${RANDOM}"
JOIN_JSON="$(cat <<EOF
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${IDEMPOTENCY_KEY}"}
EOF
)"

echo "=== Owner creates ticket ==="
JOIN_CODE="$(
  curl -sS -o "${JOIN_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OWNER}" -b "${COOKIE_OWNER}" \
    -X POST "${BASE_URL}/v1/matchmaking/queues/${MODE}/tickets" \
    -H "Content-Type: application/json" \
    -d "${JOIN_JSON}"
)"
if [[ "${JOIN_CODE}" != "200" ]]; then
  echo "ERROR: owner join returned ${JOIN_CODE}" >&2
  echo "response=$(cat "${JOIN_BODY}")" >&2
  exit 1
fi

TICKET_ID="$(extract_json_field "$(cat "${JOIN_BODY}")" "ticket_id")"
if [[ -z "${TICKET_ID}" ]]; then
  echo "ERROR: ticket_id was not returned" >&2
  echo "response=$(cat "${JOIN_BODY}")" >&2
  exit 1
fi

echo "=== Non-owner GET must be denied ==="
GET_CODE="$(
  curl -sS -o "${GET_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OTHER}" -b "${COOKIE_OTHER}" \
    "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${GET_CODE}" != "403" && "${GET_CODE}" != "404" ]]; then
  echo "ERROR: non-owner GET expected 403/404, got ${GET_CODE}" >&2
  echo "response=$(cat "${GET_BODY}")" >&2
  exit 1
fi

echo "=== Non-owner DELETE must be denied ==="
DELETE_CODE="$(
  curl -sS -o "${DELETE_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OTHER}" -b "${COOKIE_OTHER}" \
    -X DELETE "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${DELETE_CODE}" != "403" && "${DELETE_CODE}" != "404" ]]; then
  echo "ERROR: non-owner DELETE expected 403/404, got ${DELETE_CODE}" >&2
  echo "response=$(cat "${DELETE_BODY}")" >&2
  exit 1
fi

echo "=== matchmaking-ticket-ownership test passed (ticket_id=${TICKET_ID}, get=${GET_CODE}, delete=${DELETE_CODE}) ==="
