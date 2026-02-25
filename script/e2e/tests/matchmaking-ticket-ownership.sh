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
SECOND_USERNAME="test2"
SECOND_PASSWORD="test"
MODE="casual"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>                 Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url>        Keycloak base URL (example: http://keycloak.localhost:18081)
  --realm <name>                   Keycloak realm name (default: ${REALM})
  --username <name>                Primary user username (default: ${USERNAME})
  --password <pw>                  Primary user password (default: ${PASSWORD})
  --second-username <name>         Secondary user username (default: ${SECOND_USERNAME})
  --second-password <pw>           Secondary user password (default: ${SECOND_PASSWORD})
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

KEYCLOAK_AUTHORITY="${KEYCLOAK_BASE_URL#http://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY#https://}"
KEYCLOAK_AUTHORITY="${KEYCLOAK_AUTHORITY%%/*}"

COOKIE_OWNER="$(mktemp)"
COOKIE_OTHER="$(mktemp)"
JOIN_BODY="$(mktemp)"
GET_BODY="$(mktemp)"
DELETE_BODY="$(mktemp)"
OWNER_DELETE_BODY="$(mktemp)"
cleanup() {
  rm -f \
    "${COOKIE_OWNER}" "${COOKIE_OTHER}" "${JOIN_BODY}" "${GET_BODY}" "${DELETE_BODY}" "${OWNER_DELETE_BODY}" \
    || true
}
trap cleanup EXIT

echo "=== OIDC login for owner user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_OWNER}"

echo "=== OIDC login for secondary user ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${SECOND_USERNAME}" "${SECOND_PASSWORD}" "${COOKIE_OTHER}"

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

echo "=== Owner cleanup: cancel created ticket ==="
OWNER_DELETE_CODE="$(
  curl -sS -o "${OWNER_DELETE_BODY}" -w "%{http_code}" \
    -c "${COOKIE_OWNER}" -b "${COOKIE_OWNER}" \
    -X DELETE "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${OWNER_DELETE_CODE}" != "200" ]]; then
  echo "ERROR: owner cleanup cancel returned ${OWNER_DELETE_CODE}" >&2
  echo "response=$(cat "${OWNER_DELETE_BODY}")" >&2
  exit 1
fi

echo "=== matchmaking-ticket-ownership test passed (ticket_id=${TICKET_ID}, get=${GET_CODE}, delete=${DELETE_CODE}) ==="
