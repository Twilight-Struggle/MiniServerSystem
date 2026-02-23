#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/entitlement-notification-pipeline.sh
# What: Entitlement grant -> Outbox -> NATS -> Notification の到達を検証する。
# Why: DB更新とイベント処理整合、および同一event_id再配送時の冪等性をE2Eで確認するため。

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
NATS_SERVICE="nats"
NATS_PORT="4222"
NATS_STREAM="entitlement-events"
NATS_SUBJECT="entitlement.events"
NATS_BOX_IMAGE="natsio/nats-box:0.14.5"
NATS_SCAN_LIMIT="200"

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
  --nats-service <name>          NATS Service name (default: ${NATS_SERVICE})
  --nats-port <port>             NATS Service port (default: ${NATS_PORT})
  --nats-stream <name>           NATS stream name (default: ${NATS_STREAM})
  --nats-subject <name>          NATS subject name (default: ${NATS_SUBJECT})
  --nats-box-image <image>       nats CLI image (default: ${NATS_BOX_IMAGE})
  --nats-scan-limit <n>          Number of recent stream messages to scan (default: ${NATS_SCAN_LIMIT})
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
    --nats-service) NATS_SERVICE="$2"; shift 2 ;;
    --nats-port) NATS_PORT="$2"; shift 2 ;;
    --nats-stream) NATS_STREAM="$2"; shift 2 ;;
    --nats-subject) NATS_SUBJECT="$2"; shift 2 ;;
    --nats-box-image) NATS_BOX_IMAGE="$2"; shift 2 ;;
    --nats-scan-limit) NATS_SCAN_LIMIT="$2"; shift 2 ;;
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

run_nats_box() {
  local command="$1"
  local pod_name="nats-box-e2e-$(date +%s)-${RANDOM}"
  local phase=""
  local logs=""
  local pod_overrides=""

  # nats-box is only a temporary E2E helper pod.
  # In CI namespace, Istio sidecar auto-injection is enabled by default.
  # For direct NATS (non-mesh sidecar=false) access on 4222, disable injection here.
  pod_overrides='{"apiVersion":"v1","metadata":{"annotations":{"sidecar.istio.io/inject":"false"}}}'

  kubectl -n "${NAMESPACE}" run "${pod_name}" \
    --image="${NATS_BOX_IMAGE}" \
    --labels="app=e2e-nats-box" \
    --restart=Never \
    --overrides="${pod_overrides}" \
    --command -- sh -c "${command}" >/dev/null

  local end=$((SECONDS + TIMEOUT_SEC))
  while [[ ${SECONDS} -lt ${end} ]]; do
    phase="$(kubectl -n "${NAMESPACE}" get pod "${pod_name}" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    if [[ "${phase}" == "Succeeded" || "${phase}" == "Failed" ]]; then
      break
    fi
    sleep 1
  done

  logs="$(kubectl -n "${NAMESPACE}" logs "${pod_name}" 2>&1 || true)"
  kubectl -n "${NAMESPACE}" delete pod "${pod_name}" --ignore-not-found >/dev/null 2>&1 || true

  if [[ "${phase}" != "Succeeded" ]]; then
    echo "ERROR: nats-box command failed (phase=${phase:-unknown})" >&2
    echo "${logs}" >&2
    return 1
  fi
  printf '%s\n' "${logs}"
}

find_event_payload_in_stream() {
  local event_id="$1"
  local nats_server="nats://${NATS_SERVICE}:${NATS_PORT}"
  run_nats_box "
set -euo pipefail
stream_info_json=\$(nats --server ${nats_server} stream info ${NATS_STREAM} -j)
last_seq=\$(printf '%s' \"\${stream_info_json}\" | sed -n 's/.*\"last_seq\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p' | head -n1)
if [ -z \"\${last_seq}\" ] || [ \"\${last_seq}\" = \"0\" ]; then
  exit 1
fi
lower_bound=\$((last_seq - ${NATS_SCAN_LIMIT} + 1))
if [ \"\${lower_bound}\" -lt 1 ]; then
  lower_bound=1
fi
seq=\${last_seq}
while [ \"\${seq}\" -ge \"\${lower_bound}\" ]; do
  msg_json=\$(nats --server ${nats_server} stream get ${NATS_STREAM} \"\${seq}\" -j 2>/dev/null || true)
  hdrs_b64=\$(printf '%s' \"\${msg_json}\" | sed -n 's/.*\"hdrs\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' | head -n1)
  data_b64=\$(printf '%s' \"\${msg_json}\" | sed -n 's/.*\"data\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' | head -n1)
  if [ -n \"\${data_b64}\" ] && printf '%s' \"\${data_b64}\" | base64 -d 2>/dev/null | grep -F -q '${event_id}'; then
    printf '%s' \"\${data_b64}\"
    exit 0
  fi
  if [ -n \"\${hdrs_b64}\" ]; then
    headers=\$(printf '%s' \"\${hdrs_b64}\" | base64 -d 2>/dev/null || true)
    if printf '%s' \"\${headers}\" | grep -q 'Nats-Msg-Id: ${event_id}'; then
      if [ -n \"\${data_b64}\" ]; then
        printf '%s' \"\${data_b64}\"
        exit 0
      fi
    fi
  fi
  seq=\$((seq - 1))
done
exit 1
"
}

wait_for_exact_counts() {
  local expected_notification_count="$1"
  local expected_processed_count="$2"
  local event_id="$3"
  local user_id="$4"
  local notification_count="-1"
  local processed_count="-1"
  local end=$((SECONDS + TIMEOUT_SEC))
  while [[ ${SECONDS} -lt ${end} ]]; do
    notification_count="$(
      query_postgres "SELECT COUNT(*) FROM notification.notifications WHERE user_id = '${user_id}';" \
        | tr -d '[:space:]'
    )"
    processed_count="$(
      query_postgres "SELECT COUNT(*) FROM notification.processed_events WHERE event_id = '${event_id}'::uuid;" \
        | tr -d '[:space:]'
    )"
    if [[ "${notification_count}" == "${expected_notification_count}" && "${processed_count}" == "${expected_processed_count}" ]]; then
      return 0
    fi
    sleep "${POLL_INTERVAL_SEC}"
  done
  echo "ERROR: expected notifications=${expected_notification_count}, processed_events=${expected_processed_count}, actual notifications=${notification_count}, processed_events=${processed_count}" >&2
  return 1
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

echo "=== Republish same event_id via NATS and verify idempotency ==="
PAYLOAD_B64="$(
  find_event_payload_in_stream "${EVENT_ID}" \
    | tr -d '\r' \
    | awk '/^[A-Za-z0-9+\/=]+$/ { last=$0 } END { print last }' \
    || true
)"
if [[ -z "${PAYLOAD_B64}" ]]; then
  echo "ERROR: could not find payload in stream=${NATS_STREAM} for event_id=${EVENT_ID}" >&2
  exit 1
fi

run_nats_box "payload_b64='${PAYLOAD_B64}'; printf '%s' \"\${payload_b64}\" | base64 -d | nats --server nats://${NATS_SERVICE}:${NATS_PORT} publish ${NATS_SUBJECT} --force-stdin" >/dev/null

wait_for_exact_counts "1" "1" "${EVENT_ID}" "${USER_ID}" || {
  query_postgres "SELECT notification_id::text, event_id::text, user_id, type, status FROM notification.notifications WHERE user_id = '${USER_ID}' ORDER BY created_at;"
  query_postgres "SELECT event_id::text, processed_at FROM notification.processed_events WHERE event_id = '${EVENT_ID}'::uuid;"
  exit 1
}

echo "=== entitlement-notification-pipeline test passed ==="
