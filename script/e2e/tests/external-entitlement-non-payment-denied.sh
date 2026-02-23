#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/external-entitlement-non-payment-denied.sh
# What: Verify entitlement API except payment endpoints is not externally reachable.
# Why: Only grants/revokes should be exposed to external payment service.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
TIMEOUT_SEC="120"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> [options]

Options:
  --base-url <url>       External ingress base URL (example: http://127.0.0.1:18080)
  --timeout-sec <sec>    Wait timeout in seconds (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "${BASE_URL}" ]]; then
  usage
  exit 2
fi

require_cmd curl

wait_for_endpoint "${BASE_URL}/actuator/health/readiness" "${TIMEOUT_SEC}" "gateway readiness" || {
  echo "ERROR: gateway readiness did not become healthy within ${TIMEOUT_SEC}s" >&2
  exit 1
}

TARGET_PATH="/v1/users/external-test-user/entitlements"
BODY_FILE="$(mktemp)"
trap 'rm -f "${BODY_FILE}" || true' EXIT
code="$(
  curl -sS -o "${BODY_FILE}" -w "%{http_code}" \
    "${BASE_URL}${TARGET_PATH}"
)"

if [[ "${code}" == "200" ]]; then
  echo "ERROR: expected deny for ${TARGET_PATH}, but got status=200" >&2
  echo "response=$(cat "${BODY_FILE}")" >&2
  exit 1
fi

echo "Denied as expected: ${TARGET_PATH} -> status=${code}"

echo "=== external-entitlement-non-payment-denied test passed ==="
