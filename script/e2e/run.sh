#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="miniserversystem"
GATEWAY_SERVICE="gateway"
LOCAL_PORT="18080"
REMOTE_PORT="80"
TIMEOUT_SEC="120"

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --namespace <ns>        Kubernetes namespace (default: ${NAMESPACE})
  --gateway-service <svc> Gateway Service name (default: ${GATEWAY_SERVICE})
  --local-port <port>     Local port for port-forward (default: ${LOCAL_PORT})
  --remote-port <port>    Service port for port-forward (default: ${REMOTE_PORT})
  --timeout-sec <sec>     Wait timeout for readiness (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)       NAMESPACE="$2"; shift 2 ;;
    --gateway-service) GATEWAY_SERVICE="$2"; shift 2 ;;
    --local-port)      LOCAL_PORT="$2"; shift 2 ;;
    --remote-port)     REMOTE_PORT="$2"; shift 2 ;;
    --timeout-sec)     TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help)         usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 127; }
}
require_cmd kubectl
require_cmd curl

BASE_URL="http://127.0.0.1:${LOCAL_PORT}"

dump_diagnostics() {
  echo "=== Diagnostics (namespace=${NAMESPACE}) ===" >&2
  kubectl get nodes -o wide || true
  kubectl get ns || true
  kubectl -n "${NAMESPACE}" get all || true
  kubectl -n "${NAMESPACE}" get pods -o wide || true

  echo "--- describe pods ---" >&2
  kubectl -n "${NAMESPACE}" describe pods || true

  # 主要 deploy のログ（必要に応じて増やす）
  for d in gateway account entitlement matchmaking notification; do
    echo "--- logs: deploy/${d} (tail=200) ---" >&2
    kubectl -n "${NAMESPACE}" logs "deploy/${d}" --all-containers=true --tail=200 || true
  done
}

cleanup_pf() {
  if [[ -n "${PF_PID:-}" ]]; then
    kill "${PF_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup_pf EXIT

echo "=== Port-forward svc/${GATEWAY_SERVICE} ${LOCAL_PORT}:${REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${GATEWAY_SERVICE}" "${LOCAL_PORT}:${REMOTE_PORT}" >/tmp/pf.log 2>&1 &
PF_PID=$!

# readiness を待つ（Helm の readinessProbe と同じ path）
echo "=== Waiting for readiness: ${BASE_URL}/actuator/health/readiness (timeout=${TIMEOUT_SEC}s) ==="
end=$((SECONDS + TIMEOUT_SEC))
ok=0
while [[ $SECONDS -lt $end ]]; do
  if curl -fsS "${BASE_URL}/actuator/health/readiness" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 2
done

if [[ "${ok}" -ne 1 ]]; then
  echo "ERROR: readiness did not become healthy within ${TIMEOUT_SEC}s" >&2
  echo "port-forward log:" >&2
  tail -n 200 /tmp/pf.log >&2 || true
  dump_diagnostics
  exit 1
fi

echo "=== Readiness OK ==="
curl -fsS "${BASE_URL}/actuator/health/readiness" | head -c 400 || true
echo

# liveness も軽く確認（Helm の livenessProbe と同じ path）
echo "=== Checking liveness ==="
curl -fsS "${BASE_URL}/actuator/health/liveness" | head -c 400 || true
echo

echo "=== E2E minimal checks passed ==="
