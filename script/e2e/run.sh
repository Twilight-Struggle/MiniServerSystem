#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/run.sh
# What: E2E test orchestrator for running split scenario scripts.
# Why: Keep test scenarios modular and easier to maintain as test cases grow.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

NAMESPACE="miniserversystem"
GATEWAY_SERVICE="gateway"
LOCAL_PORT="18080"
REMOTE_PORT="80"
KEYCLOAK_SERVICE="keycloak"
KEYCLOAK_LOCAL_PORT="18081"
KEYCLOAK_REMOTE_PORT="8080"
REALM="miniserversystem"
USERNAME="test"
PASSWORD="test"
TIMEOUT_SEC="120"

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --namespace <ns>        Kubernetes namespace (default: ${NAMESPACE})
  --gateway-service <svc> Gateway Service name (default: ${GATEWAY_SERVICE})
  --local-port <port>     Local port for port-forward (default: ${LOCAL_PORT})
  --remote-port <port>    Service port for port-forward (default: ${REMOTE_PORT})
  --keycloak-service <s>  Keycloak Service name (default: ${KEYCLOAK_SERVICE})
  --keycloak-local-port <p>
                          Local Keycloak port-forward port (default: ${KEYCLOAK_LOCAL_PORT})
  --keycloak-remote-port <p>
                          Keycloak Service port (default: ${KEYCLOAK_REMOTE_PORT})
  --realm <name>          Keycloak realm name (default: ${REALM})
  --username <name>       Keycloak test user (default: ${USERNAME})
  --password <pw>         Keycloak test password (default: ${PASSWORD})
  --timeout-sec <sec>     Wait timeout for readiness (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)       NAMESPACE="$2"; shift 2 ;;
    --gateway-service) GATEWAY_SERVICE="$2"; shift 2 ;;
    --local-port)      LOCAL_PORT="$2"; shift 2 ;;
    --remote-port)     REMOTE_PORT="$2"; shift 2 ;;
    --keycloak-service) KEYCLOAK_SERVICE="$2"; shift 2 ;;
    --keycloak-local-port) KEYCLOAK_LOCAL_PORT="$2"; shift 2 ;;
    --keycloak-remote-port) KEYCLOAK_REMOTE_PORT="$2"; shift 2 ;;
    --realm) REALM="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --timeout-sec)     TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help)         usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

require_cmd kubectl
require_cmd curl

BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
KEYCLOAK_BASE_URL="http://127.0.0.1:${KEYCLOAK_LOCAL_PORT}"
KEYCLOAK_WELL_KNOWN_URL="${KEYCLOAK_BASE_URL}/realms/${REALM}/.well-known/openid-configuration"

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
  if [[ -n "${GATEWAY_PF_PID:-}" ]]; then
    kill "${GATEWAY_PF_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${KEYCLOAK_PF_PID:-}" ]]; then
    kill "${KEYCLOAK_PF_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup_pf EXIT

run_test_script() {
  local test_name="$1"
  shift
  echo "=== Running E2E test: ${test_name} ==="
  if ! bash "$@"; then
    echo "ERROR: E2E test failed: ${test_name}" >&2
    echo "--- gateway port-forward log ---" >&2
    tail -n 200 /tmp/pf.log >&2 || true
    echo "--- keycloak port-forward log ---" >&2
    tail -n 200 /tmp/pf-keycloak.log >&2 || true
    dump_diagnostics
    exit 1
  fi
}

echo "=== Port-forward svc/${GATEWAY_SERVICE} ${LOCAL_PORT}:${REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${GATEWAY_SERVICE}" "${LOCAL_PORT}:${REMOTE_PORT}" >/tmp/pf.log 2>&1 &
GATEWAY_PF_PID=$!

echo "=== Port-forward svc/${KEYCLOAK_SERVICE} ${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${KEYCLOAK_SERVICE}" "${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_REMOTE_PORT}" >/tmp/pf-keycloak.log 2>&1 &
KEYCLOAK_PF_PID=$!

run_test_script \
  "health" \
  "${SCRIPT_DIR}/tests/health.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-well-known-url "${KEYCLOAK_WELL_KNOWN_URL}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "oidc-user-ownership" \
  "${SCRIPT_DIR}/tests/oidc-user-ownership.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}"

echo "=== All E2E tests passed ==="
