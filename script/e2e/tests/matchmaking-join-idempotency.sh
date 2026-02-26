#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/matchmaking-join-idempotency.sh
# What: Verify Join API idempotency for identical idempotency_key.
# Why: Prevent duplicate ticket creation on client retry.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
USERNAME="test"
PASSWORD="test"
MODE="casual"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>          Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url> Keycloak base URL (example: http://keycloak.localhost:18081)
  --username <name>         Keycloak test username (default: ${USERNAME})
  --password <pw>           Keycloak test password (default: ${PASSWORD})
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
JOIN_BODY_1="$(mktemp)"
JOIN_BODY_2="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${JOIN_BODY_1}" "${JOIN_BODY_2}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_JAR}"

IDEMPOTENCY_KEY="e2e-mm-idemp-$(date +%s)-${RANDOM}"
JOIN_JSON="$(cat <<EOF
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${IDEMPOTENCY_KEY}"}
EOF
)"

echo "=== Join matchmaking ticket (first request) ==="
JOIN_CODE_1="$(
  curl -sS -o "${JOIN_BODY_1}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -X POST "${BASE_URL}/v1/matchmaking/queues/${MODE}/tickets" \
    -H "Content-Type: application/json" \
    -d "${JOIN_JSON}"
)"
if [[ "${JOIN_CODE_1}" != "200" ]]; then
  echo "ERROR: first join returned ${JOIN_CODE_1}" >&2
  echo "response=$(cat "${JOIN_BODY_1}")" >&2
  exit 1
fi

echo "=== Join matchmaking ticket (retry with same idempotency_key) ==="
JOIN_CODE_2="$(
  curl -sS -o "${JOIN_BODY_2}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -X POST "${BASE_URL}/v1/matchmaking/queues/${MODE}/tickets" \
    -H "Content-Type: application/json" \
    -d "${JOIN_JSON}"
)"
if [[ "${JOIN_CODE_2}" != "200" ]]; then
  echo "ERROR: second join returned ${JOIN_CODE_2}" >&2
  echo "response=$(cat "${JOIN_BODY_2}")" >&2
  exit 1
fi

TICKET_ID_1="$(extract_json_field "$(cat "${JOIN_BODY_1}")" "ticket_id")"
TICKET_ID_2="$(extract_json_field "$(cat "${JOIN_BODY_2}")" "ticket_id")"
STATUS_1="$(extract_json_field "$(cat "${JOIN_BODY_1}")" "status")"
STATUS_2="$(extract_json_field "$(cat "${JOIN_BODY_2}")" "status")"

if [[ -z "${TICKET_ID_1}" || -z "${TICKET_ID_2}" ]]; then
  echo "ERROR: ticket_id was not returned" >&2
  echo "first=$(cat "${JOIN_BODY_1}")" >&2
  echo "second=$(cat "${JOIN_BODY_2}")" >&2
  exit 1
fi

if [[ "${TICKET_ID_1}" != "${TICKET_ID_2}" ]]; then
  echo "ERROR: idempotent join returned different ticket_id" >&2
  echo "first=${TICKET_ID_1}" >&2
  echo "second=${TICKET_ID_2}" >&2
  exit 1
fi

if [[ -z "${STATUS_1}" || -z "${STATUS_2}" ]]; then
  echo "ERROR: status was not returned" >&2
  echo "first=$(cat "${JOIN_BODY_1}")" >&2
  echo "second=$(cat "${JOIN_BODY_2}")" >&2
  exit 1
fi

echo "=== matchmaking-join-idempotency test passed (ticket_id=${TICKET_ID_1}, status1=${STATUS_1}, status2=${STATUS_2}) ==="
