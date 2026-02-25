#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/matchmaking-cancel-idempotency.sh
# What: Verify repeated cancel requests are idempotent and stable.
# Why: Prevent retry/replay from causing inconsistent ticket states.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
USERNAME="test"
PASSWORD="test"
MODE="rank"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>          Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url> Keycloak base URL (example: http://keycloak.localhost:18081)
  --username <name>         Keycloak username (default: ${USERNAME})
  --password <pw>           Keycloak password (default: ${PASSWORD})
  --mode <mode>             Matchmaking mode (default: ${MODE})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-base-url) KEYCLOAK_BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
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

COOKIE_JAR="$(mktemp)"
JOIN_BODY="$(mktemp)"
CANCEL_BODY_1="$(mktemp)"
CANCEL_BODY_2="$(mktemp)"
STATUS_BODY="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${JOIN_BODY}" "${CANCEL_BODY_1}" "${CANCEL_BODY_2}" "${STATUS_BODY}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_JAR}"

IDEMPOTENCY_KEY="e2e-mm-cancel-$(date +%s)-${RANDOM}"
JOIN_JSON="$(cat <<EOF
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${IDEMPOTENCY_KEY}"}
EOF
)"

echo "=== Create ticket ==="
JOIN_CODE="$(
  curl -sS -o "${JOIN_BODY}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -X POST "${BASE_URL}/v1/matchmaking/queues/${MODE}/tickets" \
    -H "Content-Type: application/json" \
    -d "${JOIN_JSON}"
)"
if [[ "${JOIN_CODE}" != "200" ]]; then
  echo "ERROR: join returned ${JOIN_CODE}" >&2
  echo "response=$(cat "${JOIN_BODY}")" >&2
  exit 1
fi

TICKET_ID="$(extract_json_field "$(cat "${JOIN_BODY}")" "ticket_id")"
if [[ -z "${TICKET_ID}" ]]; then
  echo "ERROR: ticket_id is missing" >&2
  echo "response=$(cat "${JOIN_BODY}")" >&2
  exit 1
fi

echo "=== Cancel ticket (1st call) ==="
CANCEL_CODE_1="$(
  curl -sS -o "${CANCEL_BODY_1}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -X DELETE "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${CANCEL_CODE_1}" != "200" ]]; then
  echo "ERROR: first cancel returned ${CANCEL_CODE_1}" >&2
  echo "response=$(cat "${CANCEL_BODY_1}")" >&2
  exit 1
fi

echo "=== Cancel ticket (2nd call) ==="
CANCEL_CODE_2="$(
  curl -sS -o "${CANCEL_BODY_2}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -X DELETE "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${CANCEL_CODE_2}" != "200" ]]; then
  echo "ERROR: second cancel returned ${CANCEL_CODE_2}" >&2
  echo "response=$(cat "${CANCEL_BODY_2}")" >&2
  exit 1
fi

CANCEL_STATUS_1="$(extract_json_field "$(cat "${CANCEL_BODY_1}")" "status")"
CANCEL_STATUS_2="$(extract_json_field "$(cat "${CANCEL_BODY_2}")" "status")"
if [[ "${CANCEL_STATUS_1}" != "CANCELLED" || "${CANCEL_STATUS_2}" != "CANCELLED" ]]; then
  echo "ERROR: cancel should return CANCELLED on both calls" >&2
  echo "cancel1=$(cat "${CANCEL_BODY_1}")" >&2
  echo "cancel2=$(cat "${CANCEL_BODY_2}")" >&2
  exit 1
fi

echo "=== Verify final ticket status remains CANCELLED ==="
GET_CODE="$(
  curl -sS -o "${STATUS_BODY}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
)"
if [[ "${GET_CODE}" != "200" ]]; then
  echo "ERROR: status lookup returned ${GET_CODE}" >&2
  echo "response=$(cat "${STATUS_BODY}")" >&2
  exit 1
fi

FINAL_STATUS="$(extract_json_field "$(cat "${STATUS_BODY}")" "status")"
if [[ "${FINAL_STATUS}" != "CANCELLED" ]]; then
  echo "ERROR: final status expected CANCELLED, actual=${FINAL_STATUS}" >&2
  echo "response=$(cat "${STATUS_BODY}")" >&2
  exit 1
fi

echo "=== matchmaking-cancel-idempotency test passed (ticket_id=${TICKET_ID}) ==="
