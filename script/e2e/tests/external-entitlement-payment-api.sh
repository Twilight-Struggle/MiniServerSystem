#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/external-entitlement-payment-api.sh
# What: Verify grants/revokes APIs are reachable from external ingress path.
# Why: Payment service is out-of-scope but must be able to trigger entitlement updates.

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

USER_ID="e2e-payment-user-$(date +%s)-${RANDOM}"
SKU="e2e.payment.sku"
PURCHASE_ID="e2e-payment-$(date +%s)-${RANDOM}"
REQUEST_BODY="$(cat <<EOF
{"user_id":"${USER_ID}","stock_keeping_unit":"${SKU}","reason":"purchase","purchase_id":"${PURCHASE_ID}"}
EOF
)"

call_payment_api() {
  local path="$1"
  local idem="$2"
  local trace="$3"
  local body_file
  local code
  body_file="$(mktemp)"
  code="$(
    curl -sS -o "${body_file}" -w "%{http_code}" \
      -X POST "${BASE_URL}${path}" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: ${idem}" \
      -H "X-Trace-Id: ${trace}" \
      -d "${REQUEST_BODY}"
  )"
  if [[ "${code}" != "200" ]]; then
    echo "ERROR: POST ${path} returned ${code}" >&2
    echo "response=$(cat "${body_file}")" >&2
    rm -f "${body_file}" || true
    exit 1
  fi
  rm -f "${body_file}" || true
}

echo "=== Call external payment API: grants ==="
call_payment_api \
  "/v1/entitlements/grants" \
  "e2e-ext-grant-${PURCHASE_ID}" \
  "e2e-ext-grant-trace-$(date +%s)-${RANDOM}"

echo "=== Call external payment API: revokes ==="
call_payment_api \
  "/v1/entitlements/revokes" \
  "e2e-ext-revoke-${PURCHASE_ID}" \
  "e2e-ext-revoke-trace-$(date +%s)-${RANDOM}"

echo "=== external-entitlement-payment-api test passed ==="
