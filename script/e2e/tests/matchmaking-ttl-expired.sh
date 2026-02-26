#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/matchmaking-ttl-expired.sh
# What: Verify ticket eventually transitions to EXPIRED when unmatched.
# Why: Prevent stale queued tickets from remaining matchable indefinitely.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
USERNAME="test"
PASSWORD="test"
MODE="rank"
TIMEOUT_SEC="120"
POLL_INTERVAL_SEC="2"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>          Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url> Keycloak base URL (example: http://keycloak.localhost:18081)
  --username <name>         Keycloak username (default: ${USERNAME})
  --password <pw>           Keycloak password (default: ${PASSWORD})
  --mode <mode>             Matchmaking mode (default: ${MODE})
  --timeout-sec <sec>       Poll timeout in seconds (default: ${TIMEOUT_SEC})
  --poll-interval-sec <sec> Poll interval in seconds (default: ${POLL_INTERVAL_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-base-url) KEYCLOAK_BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
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

COOKIE_JAR="$(mktemp)"
JOIN_BODY="$(mktemp)"
STATUS_BODY="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${JOIN_BODY}" "${STATUS_BODY}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_JAR}"

IDEMPOTENCY_KEY="e2e-mm-expire-$(date +%s)-${RANDOM}"
JOIN_JSON="$(cat <<EOF
{"party_size":1,"attributes":{"region":"apne1"},"idempotency_key":"${IDEMPOTENCY_KEY}"}
EOF
)"

echo "=== Create ticket and wait for EXPIRED (or purged as NOT_FOUND) ==="
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

FINAL_STATUS=""
END=$((SECONDS + TIMEOUT_SEC))
while [[ ${SECONDS} -lt ${END} ]]; do
  STATUS_CODE="$(
    curl -sS -o "${STATUS_BODY}" -w "%{http_code}" \
      -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      "${BASE_URL}/v1/matchmaking/tickets/${TICKET_ID}"
  )"
  if [[ "${STATUS_CODE}" == "404" ]]; then
    ERROR_CODE="$(extract_json_field "$(cat "${STATUS_BODY}")" "code")"
    if [[ "${ERROR_CODE}" == "MATCHMAKING_NOT_FOUND" ]]; then
      # Current implementation sets Redis key TTL to ticket-ttl.
      # After expiry, key may be physically purged and surfaced as NOT_FOUND.
      FINAL_STATUS="EXPIRED_OR_PURGED"
      break
    fi
  fi
  if [[ "${STATUS_CODE}" != "200" ]]; then
    echo "ERROR: status lookup returned ${STATUS_CODE}" >&2
    echo "response=$(cat "${STATUS_BODY}")" >&2
    exit 1
  fi
  FINAL_STATUS="$(extract_json_field "$(cat "${STATUS_BODY}")" "status")"
  if [[ "${FINAL_STATUS}" == "EXPIRED" ]]; then
    break
  fi
  if [[ "${FINAL_STATUS}" == "MATCHED" ]]; then
    echo "ERROR: ticket unexpectedly matched while waiting for expiry" >&2
    echo "response=$(cat "${STATUS_BODY}")" >&2
    exit 1
  fi
  sleep "${POLL_INTERVAL_SEC}"
done

if [[ "${FINAL_STATUS}" != "EXPIRED" && "${FINAL_STATUS}" != "EXPIRED_OR_PURGED" ]]; then
  echo "ERROR: ticket did not reach EXPIRED (or purged NOT_FOUND) within timeout=${TIMEOUT_SEC}s" >&2
  echo "last_response=$(cat "${STATUS_BODY}")" >&2
  exit 1
fi

echo "=== matchmaking-ttl-expired test passed (ticket_id=${TICKET_ID}) ==="
