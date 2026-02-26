#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/oidc-profile-aggregate.sh
# What: Verify profile aggregate endpoint returns integrated data for owner and denies other user.
# Why: Ensure BFF profile aggregation keeps ownership boundary and downstream integration consistency.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
USERNAME="test"
PASSWORD="test"
SECOND_USERNAME="test2"
SECOND_PASSWORD="test"
MODE="casual"

usage() {
  cat <<EOF_USAGE
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>                 Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url>        Keycloak base URL (example: http://keycloak.localhost:18081)
  --username <name>                Primary user username (default: ${USERNAME})
  --password <pw>                  Primary user password (default: ${PASSWORD})
  --second-username <name>         Secondary user username (default: ${SECOND_USERNAME})
  --second-password <pw>           Secondary user password (default: ${SECOND_PASSWORD})
  --mode <mode>                    Matchmaking mode (default: ${MODE})
EOF_USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-base-url) KEYCLOAK_BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --second-username) SECOND_USERNAME="$2"; shift 2 ;;
    --second-password) SECOND_PASSWORD="$2"; shift 2 ;;
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

COOKIE_OWNER="$(mktemp)"
COOKIE_OTHER="$(mktemp)"
JOIN_BODY="$(mktemp)"
PROFILE_BODY="$(mktemp)"
FORBIDDEN_BODY="$(mktemp)"
CANCEL_BODY="$(mktemp)"
cleanup() {
  rm -f \
    "${COOKIE_OWNER}" "${COOKIE_OTHER}" "${JOIN_BODY}" "${PROFILE_BODY}" "${FORBIDDEN_BODY}" "${CANCEL_BODY}" \
    || true
}
trap cleanup EXIT

echo "=== OIDC login for owner user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_OWNER}"

echo "=== OIDC login for secondary user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${SECOND_USERNAME}" "${SECOND_PASSWORD}" "${COOKIE_OTHER}"

OWNER_USER_ID="$(fetch_me_user_id "${BASE_URL}" "${COOKIE_OWNER}")"
OTHER_USER_ID="$(fetch_me_user_id "${BASE_URL}" "${COOKIE_OTHER}")"

IDEMPOTENCY_KEY="e2e-profile-mm-$(date +%s)-${RANDOM}"
JOIN_JSON="$(cat <<EOF_JOIN
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${IDEMPOTENCY_KEY}"}
EOF_JOIN
)"

echo "=== Owner creates matchmaking ticket for profile aggregate ==="
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

echo "=== Owner gets profile aggregate with ticketId ==="
PROFILE_CODE="$(
  curl -sS -o "${PROFILE_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OWNER}" -b "${COOKIE_OWNER}" \
    "${BASE_URL}/v1/users/${OWNER_USER_ID}/profile?ticketId=${TICKET_ID}"
)"
if [[ "${PROFILE_CODE}" != "200" ]]; then
  echo "ERROR: GET /v1/users/${OWNER_USER_ID}/profile returned ${PROFILE_CODE}" >&2
  echo "response=$(cat "${PROFILE_BODY}")" >&2
  exit 1
fi

PROFILE_JSON="$(cat "${PROFILE_BODY}")"
ACCOUNT_USER_ID="$(
  printf '%s' "${PROFILE_JSON}" \
    | perl -0777 -ne 'if (/"account"\s*:\s*\{.*?"user_id"\s*:\s*"([^"]+)"/s) { print $1; }'
)"
ENTITLEMENT_USER_ID="$(
  printf '%s' "${PROFILE_JSON}" \
    | perl -0777 -ne 'if (/"entitlement"\s*:\s*\{.*?"user_id"\s*:\s*"([^"]+)"/s) { print $1; }'
)"
MATCHMAKING_TICKET_ID="$(
  printf '%s' "${PROFILE_JSON}" \
    | perl -0777 -ne 'if (/"matchmaking"\s*:\s*\{.*?"ticket_id"\s*:\s*"([^"]+)"/s) { print $1; }'
)"

if [[ "${ACCOUNT_USER_ID}" != "${OWNER_USER_ID}" ]]; then
  echo "ERROR: account.user_id mismatch (expected=${OWNER_USER_ID}, actual=${ACCOUNT_USER_ID})" >&2
  echo "response=${PROFILE_JSON}" >&2
  exit 1
fi
if [[ "${ENTITLEMENT_USER_ID}" != "${OWNER_USER_ID}" ]]; then
  echo "ERROR: entitlement.user_id mismatch (expected=${OWNER_USER_ID}, actual=${ENTITLEMENT_USER_ID})" >&2
  echo "response=${PROFILE_JSON}" >&2
  exit 1
fi
if [[ "${MATCHMAKING_TICKET_ID}" != "${TICKET_ID}" ]]; then
  echo "ERROR: matchmaking.ticket_id mismatch (expected=${TICKET_ID}, actual=${MATCHMAKING_TICKET_ID})" >&2
  echo "response=${PROFILE_JSON}" >&2
  exit 1
fi

echo "=== Owner tries another user's profile and must be forbidden ==="
FORBIDDEN_CODE="$(
  curl -sS -o "${FORBIDDEN_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OWNER}" -b "${COOKIE_OWNER}" \
    "${BASE_URL}/v1/users/${OTHER_USER_ID}/profile"
)"
if [[ "${FORBIDDEN_CODE}" != "403" ]]; then
  echo "ERROR: GET other profile expected 403, got ${FORBIDDEN_CODE}" >&2
  echo "response=$(cat "${FORBIDDEN_BODY}")" >&2
  exit 1
fi

FORBIDDEN_CODE_VALUE="$(extract_json_field "$(cat "${FORBIDDEN_BODY}")" "code")"
if [[ "${FORBIDDEN_CODE_VALUE}" != "PROFILE_FORBIDDEN" ]]; then
  echo "ERROR: expected PROFILE_FORBIDDEN, got ${FORBIDDEN_CODE_VALUE}" >&2
  echo "response=$(cat "${FORBIDDEN_BODY}")" >&2
  exit 1
fi

echo "=== Cleanup: owner cancels created ticket ==="
CANCEL_CODE="$(
  curl -sS -o "${CANCEL_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OWNER}" -b "${COOKIE_OWNER}" \
    -X DELETE "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${CANCEL_CODE}" != "200" ]]; then
  echo "ERROR: owner cleanup cancel returned ${CANCEL_CODE}" >&2
  echo "response=$(cat "${CANCEL_BODY}")" >&2
  exit 1
fi

echo "=== oidc-profile-aggregate test passed (user_id=${OWNER_USER_ID}, ticket_id=${TICKET_ID}) ==="
