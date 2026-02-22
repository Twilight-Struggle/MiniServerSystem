#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/notification-delivery-dlq.sh
# What: Notification の一時障害 -> リトライ -> DLQ 到達を E2E で検証する。
# Why: 失敗時の再配送回数と最終隔離動作が運用想定どおりかを担保するため。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

ENTITLEMENT_BASE_URL=""
NAMESPACE="miniserversystem"
POSTGRES_STATEFULSET="postgres"
POSTGRES_CONTAINER="postgres"
POSTGRES_DB="miniserversystem"
POSTGRES_USER="miniserversystem"
POSTGRES_PASSWORD="miniserversystem"
TIMEOUT_SEC="120"
POLL_INTERVAL_SEC="2"
MAX_ATTEMPTS="3"
TEST_USER_PREFIX="e2e-dlq-"

usage() {
  cat <<EOF
Usage: $0 --entitlement-base-url <url> [options]

Options:
  --entitlement-base-url <url>   Entitlement base URL (example: http://127.0.0.1:18082)
  --namespace <ns>               Kubernetes namespace (default: ${NAMESPACE})
  --postgres-statefulset <name>  Postgres StatefulSet name (default: ${POSTGRES_STATEFULSET})
  --postgres-container <name>    Postgres container name (default: ${POSTGRES_CONTAINER})
  --postgres-db <name>           Postgres DB name (default: ${POSTGRES_DB})
  --postgres-user <name>         Postgres user (default: ${POSTGRES_USER})
  --postgres-password <pw>       Postgres password (default: ${POSTGRES_PASSWORD})
  --timeout-sec <sec>            Poll timeout in seconds (default: ${TIMEOUT_SEC})
  --poll-interval-sec <sec>      Poll interval in seconds (default: ${POLL_INTERVAL_SEC})
  --max-attempts <n>             Retry attempts before DLQ (default: ${MAX_ATTEMPTS})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --entitlement-base-url) ENTITLEMENT_BASE_URL="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --postgres-statefulset) POSTGRES_STATEFULSET="$2"; shift 2 ;;
    --postgres-container) POSTGRES_CONTAINER="$2"; shift 2 ;;
    --postgres-db) POSTGRES_DB="$2"; shift 2 ;;
    --postgres-user) POSTGRES_USER="$2"; shift 2 ;;
    --postgres-password) POSTGRES_PASSWORD="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    --poll-interval-sec) POLL_INTERVAL_SEC="$2"; shift 2 ;;
    --max-attempts) MAX_ATTEMPTS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "${ENTITLEMENT_BASE_URL}" ]]; then
  usage
  exit 2
fi

require_cmd curl
require_cmd kubectl

wait_for_notification_deployment() {
  echo "=== Waiting for notification deployment rollout (ns=${NAMESPACE}) ==="
  kubectl -n "${NAMESPACE}" rollout status deploy/notification --timeout="${TIMEOUT_SEC}s" >/dev/null
}

query_postgres() {
  local sql="$1"
  kubectl -n "${NAMESPACE}" exec "statefulset/${POSTGRES_STATEFULSET}" -c "${POSTGRES_CONTAINER}" -- \
    env PGPASSWORD="${POSTGRES_PASSWORD}" \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "${sql}" | tr -d '\r'
}

reset_notification_env() {
  echo "=== Restoring notification delivery env defaults ==="
  kubectl -n "${NAMESPACE}" set env deploy/notification \
    NOTIFICATION_DELIVERY_MAX_ATTEMPTS=10 \
    NOTIFICATION_DELIVERY_BACKOFF_BASE=1s \
    NOTIFICATION_DELIVERY_BACKOFF_MAX=60s \
    NOTIFICATION_DELIVERY_BACKOFF_MIN=1s \
    NOTIFICATION_DELIVERY_FAILURE_INJECTION_ENABLED=false \
    NOTIFICATION_DELIVERY_FAILURE_INJECTION_USER_ID_PREFIX= >/dev/null
  wait_for_notification_deployment
}

cleanup() {
  rm -f "${GRANT_BODY_FILE:-}" || true
  reset_notification_env || true
}
trap cleanup EXIT

wait_for_endpoint "${ENTITLEMENT_BASE_URL}/actuator/health/readiness" "${TIMEOUT_SEC}" "entitlement readiness" || {
  echo "ERROR: entitlement readiness did not become healthy within ${TIMEOUT_SEC}s" >&2
  exit 1
}
wait_for_notification_deployment

USER_ID="${TEST_USER_PREFIX}user-$(date +%s)-${RANDOM}"
SKU="e2e.sku.dlq"
PURCHASE_ID="e2e-purchase-dlq-$(date +%s)-${RANDOM}"
IDEMPOTENCY_KEY="e2e-idempotency-${PURCHASE_ID}"
TRACE_ID="e2e-trace-dlq-$(date +%s)-${RANDOM}"
GRANT_BODY_FILE="$(mktemp)"

echo "=== Enable failure injection for Notification (user_id prefix=${USER_ID}) ==="
kubectl -n "${NAMESPACE}" set env deploy/notification \
  NOTIFICATION_DELIVERY_MAX_ATTEMPTS="${MAX_ATTEMPTS}" \
  NOTIFICATION_DELIVERY_BACKOFF_BASE=1s \
  NOTIFICATION_DELIVERY_BACKOFF_MAX=1s \
  NOTIFICATION_DELIVERY_BACKOFF_MIN=1s \
  NOTIFICATION_DELIVERY_FAILURE_INJECTION_ENABLED=true \
  NOTIFICATION_DELIVERY_FAILURE_INJECTION_USER_ID_PREFIX="${USER_ID}" >/dev/null
wait_for_notification_deployment

GRANT_JSON="$(cat <<EOF
{"user_id":"${USER_ID}","stock_keeping_unit":"${SKU}","reason":"purchase","purchase_id":"${PURCHASE_ID}"}
EOF
)"

echo "=== Grant entitlement for DLQ scenario (user_id=${USER_ID}) ==="
GRANT_CODE="$(
  curl -sS -o "${GRANT_BODY_FILE}" -w "%{http_code}" \
    -X POST "${ENTITLEMENT_BASE_URL}/v1/entitlements/grants" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -H "X-Trace-Id: ${TRACE_ID}" \
    -d "${GRANT_JSON}"
)"
if [[ "${GRANT_CODE}" != "200" ]]; then
  echo "ERROR: POST /v1/entitlements/grants returned ${GRANT_CODE}" >&2
  echo "response=$(cat "${GRANT_BODY_FILE}")" >&2
  exit 1
fi

EVENT_ID=""
STATUS=""
ATTEMPT_COUNT="0"
DLQ_COUNT="0"
echo "=== Poll notification status until FAILED + DLQ inserted ==="
end=$((SECONDS + TIMEOUT_SEC))
while [[ ${SECONDS} -lt ${end} ]]; do
  EVENT_ID="$(
    query_postgres "SELECT event_id::text FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at DESC LIMIT 1;" \
      | head -n1 | tr -d '[:space:]'
  )"
  STATUS="$(
    query_postgres "SELECT status FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at DESC LIMIT 1;" \
      | head -n1 | tr -d '[:space:]'
  )"
  ATTEMPT_COUNT="$(
    query_postgres "SELECT attempt_count FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at DESC LIMIT 1;" \
      | head -n1 | tr -d '[:space:]'
  )"
  if [[ -n "${EVENT_ID}" ]]; then
    DLQ_COUNT="$(
      query_postgres "SELECT COUNT(*) FROM notification.notification_dlq WHERE event_id = '${EVENT_ID}'::uuid;" \
        | tr -d '[:space:]'
    )"
  fi
  if [[ "${STATUS}" == "FAILED" && "${ATTEMPT_COUNT}" == "${MAX_ATTEMPTS}" && "${DLQ_COUNT}" == "1" ]]; then
    break
  fi
  sleep "${POLL_INTERVAL_SEC}"
done

if [[ "${STATUS}" != "FAILED" || "${ATTEMPT_COUNT}" != "${MAX_ATTEMPTS}" || "${DLQ_COUNT}" != "1" ]]; then
  echo "ERROR: expected status=FAILED attempt_count=${MAX_ATTEMPTS} dlq_count=1, actual status=${STATUS} attempt_count=${ATTEMPT_COUNT} dlq_count=${DLQ_COUNT}" >&2
  query_postgres "SELECT notification_id::text, event_id::text, user_id, status, attempt_count, next_retry_at, sent_at FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at;"
  if [[ -n "${EVENT_ID}" ]]; then
    query_postgres "SELECT dlq_id::text, notification_id::text, event_id::text, error_message, created_at FROM notification.notification_dlq WHERE event_id = '${EVENT_ID}'::uuid ORDER BY created_at;"
  fi
  exit 1
fi

echo "=== notification-delivery-dlq test passed ==="
