#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/entitlement-notification-pipeline.sh
# What: Entitlement grant -> Outbox -> NATS -> Notification の到達を検証する。
# Why: DB更新とイベント処理整合、at-least-once前提の冪等処理が最低限成立することをE2Eで確認するため。

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

wait_for_endpoint "${ENTITLEMENT_BASE_URL}/actuator/health/readiness" "${TIMEOUT_SEC}" "entitlement readiness" || {
  echo "ERROR: entitlement readiness did not become healthy within ${TIMEOUT_SEC}s" >&2
  exit 1
}

GRANT_BODY_FILE="$(mktemp)"
cleanup() {
  rm -f "${GRANT_BODY_FILE}" || true
}
trap cleanup EXIT

query_postgres() {
  local sql="$1"
  kubectl -n "${NAMESPACE}" exec "statefulset/${POSTGRES_STATEFULSET}" -c "${POSTGRES_CONTAINER}" -- \
    env PGPASSWORD="${POSTGRES_PASSWORD}" \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "${sql}" | tr -d '\r'
}

wait_for_notification_deployment() {
  echo "=== Waiting for notification deployment rollout (ns=${NAMESPACE}) ==="
  kubectl -n "${NAMESPACE}" rollout status deploy/notification --timeout="${TIMEOUT_SEC}s" >/dev/null
}

wait_for_notification_deployment

USER_ID="e2e-user-$(date +%s)-${RANDOM}"
SKU="e2e.sku.basic"
PURCHASE_ID="e2e-purchase-$(date +%s)-${RANDOM}"
IDEMPOTENCY_KEY="e2e-idempotency-${PURCHASE_ID}"
TRACE_ID="e2e-trace-$(date +%s)-${RANDOM}"
GRANT_JSON="$(cat <<EOF
{"user_id":"${USER_ID}","stock_keeping_unit":"${SKU}","reason":"purchase","purchase_id":"${PURCHASE_ID}"}
EOF
)"

echo "=== Grant entitlement (user_id=${USER_ID}, sku=${SKU}) ==="
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
NOTIFICATION_COUNT="0"
PROCESSED_EXISTS="f"
echo "=== Poll notification tables until event is processed ==="
end=$((SECONDS + TIMEOUT_SEC))
while [[ ${SECONDS} -lt ${end} ]]; do
  NOTIFICATION_COUNT="$(
    query_postgres "SELECT COUNT(*) FROM notification.notifications WHERE user_id = '${USER_ID}';" \
      | tr -d '[:space:]'
  )"
  EVENT_ID="$(
    query_postgres "SELECT event_id::text FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at DESC LIMIT 1;" \
      | head -n1 | tr -d '[:space:]'
  )"

  if [[ "${NOTIFICATION_COUNT}" == "1" && -n "${EVENT_ID}" ]]; then
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

if [[ "${NOTIFICATION_COUNT}" != "1" ]]; then
  echo "ERROR: expected exactly 1 notification row, actual=${NOTIFICATION_COUNT}" >&2
  query_postgres "SELECT notification_id::text, event_id::text, user_id, type, status FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at;"
  exit 1
fi

if [[ -z "${EVENT_ID}" ]]; then
  echo "ERROR: event_id was not found within ${TIMEOUT_SEC}s" >&2
  query_postgres "SELECT notification_id::text, event_id::text, user_id, type, status FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at;"
  exit 1
fi
echo "Found event_id=${EVENT_ID}"

if [[ "${PROCESSED_EXISTS}" != "t" ]]; then
  echo "ERROR: processed_events did not contain event_id=${EVENT_ID} within ${TIMEOUT_SEC}s" >&2
  query_postgres "SELECT event_id::text, processed_at FROM notification.processed_events WHERE event_id = '${EVENT_ID}'::uuid;"
  exit 1
fi

echo "=== entitlement-notification-pipeline test passed ==="
