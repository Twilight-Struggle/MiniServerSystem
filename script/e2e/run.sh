#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/run.sh
# What: E2E test orchestrator for running split scenario scripts.
# Why: Keep test scenarios modular and easier to maintain as test cases grow.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

NAMESPACE="miniserversystem"
GATEWAY_NAMESPACE="istio-system"
GATEWAY_SERVICE="istio-ingressgateway"
LOCAL_PORT="18080"
REMOTE_PORT="80"
KEYCLOAK_SERVICE="keycloak"
KEYCLOAK_LOCAL_PORT="18081"
KEYCLOAK_REMOTE_PORT="8080"
ENTITLEMENT_SERVICE="entitlement"
ENTITLEMENT_LOCAL_PORT="18082"
ENTITLEMENT_REMOTE_PORT="80"
REALM="miniserversystem"
USERNAME="test"
PASSWORD="test"
SECOND_USERNAME="test2"
SECOND_PASSWORD="test"
TIMEOUT_SEC="120"

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --namespace <ns>        Kubernetes namespace (default: ${NAMESPACE})
  --gateway-namespace <ns>
                          Kubernetes namespace for ingress gateway (default: ${GATEWAY_NAMESPACE})
  --gateway-service <svc> Gateway Service name (default: ${GATEWAY_SERVICE})
  --local-port <port>     Local port for port-forward (default: ${LOCAL_PORT})
  --remote-port <port>    Service port for port-forward (default: ${REMOTE_PORT})
  --keycloak-service <s>  Keycloak Service name (default: ${KEYCLOAK_SERVICE})
  --keycloak-local-port <p>
                          Local Keycloak port-forward port (default: ${KEYCLOAK_LOCAL_PORT})
  --keycloak-remote-port <p>
                          Keycloak Service port (default: ${KEYCLOAK_REMOTE_PORT})
  --entitlement-service <s>
                          Entitlement Service name (default: ${ENTITLEMENT_SERVICE})
  --entitlement-local-port <p>
                          Local Entitlement port-forward port (default: ${ENTITLEMENT_LOCAL_PORT})
  --entitlement-remote-port <p>
                          Entitlement Service port (default: ${ENTITLEMENT_REMOTE_PORT})
  --realm <name>          Keycloak realm name (default: ${REALM})
  --username <name>       Keycloak test user (default: ${USERNAME})
  --password <pw>         Keycloak test password (default: ${PASSWORD})
  --second-username <name> Secondary Keycloak test user (default: ${SECOND_USERNAME})
  --second-password <pw>   Secondary Keycloak test password (default: ${SECOND_PASSWORD})
  --timeout-sec <sec>     Wait timeout for readiness (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)       NAMESPACE="$2"; shift 2 ;;
    --gateway-namespace) GATEWAY_NAMESPACE="$2"; shift 2 ;;
    --gateway-service) GATEWAY_SERVICE="$2"; shift 2 ;;
    --local-port)      LOCAL_PORT="$2"; shift 2 ;;
    --remote-port)     REMOTE_PORT="$2"; shift 2 ;;
    --keycloak-service) KEYCLOAK_SERVICE="$2"; shift 2 ;;
    --keycloak-local-port) KEYCLOAK_LOCAL_PORT="$2"; shift 2 ;;
    --keycloak-remote-port) KEYCLOAK_REMOTE_PORT="$2"; shift 2 ;;
    --entitlement-service) ENTITLEMENT_SERVICE="$2"; shift 2 ;;
    --entitlement-local-port) ENTITLEMENT_LOCAL_PORT="$2"; shift 2 ;;
    --entitlement-remote-port) ENTITLEMENT_REMOTE_PORT="$2"; shift 2 ;;
    --realm) REALM="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --second-username) SECOND_USERNAME="$2"; shift 2 ;;
    --second-password) SECOND_PASSWORD="$2"; shift 2 ;;
    --timeout-sec)     TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help)         usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

require_cmd kubectl
require_cmd curl

BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
KEYCLOAK_LOCAL_BASE_URL="http://127.0.0.1:${KEYCLOAK_LOCAL_PORT}"
KEYCLOAK_BASE_URL="http://keycloak.localhost:${KEYCLOAK_LOCAL_PORT}"
KEYCLOAK_WELL_KNOWN_URL="${KEYCLOAK_LOCAL_BASE_URL}/realms/${REALM}/.well-known/openid-configuration"
ENTITLEMENT_BASE_URL="http://127.0.0.1:${ENTITLEMENT_LOCAL_PORT}"

dump_diagnostics() {
  echo "=== Diagnostics (namespace=${NAMESPACE}) ===" >&2
  kubectl get nodes -o wide || true
  kubectl get ns || true
  kubectl -n "${NAMESPACE}" get all || true
  kubectl -n "${NAMESPACE}" get pods -o wide || true

  echo "--- describe pods ---" >&2
  kubectl -n "${NAMESPACE}" describe pods || true

  echo "--- postgres state ---" >&2
  local postgres_pod=""
  postgres_pod="$(kubectl -n "${NAMESPACE}" get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -n "${postgres_pod}" ]]; then
    kubectl -n "${NAMESPACE}" exec "${postgres_pod}" -- pg_isready -U miniserversystem -d miniserversystem || true
    kubectl -n "${NAMESPACE}" exec "${postgres_pod}" -- \
      psql -U miniserversystem -d miniserversystem -c \
      "SELECT NOW() AS now_utc;" || true
  else
    echo "postgres pod not found" >&2
  fi

  echo "--- nats state ---" >&2
  local nats_pod=""
  nats_pod="$(kubectl -n "${NAMESPACE}" get pod -l app=nats -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -n "${nats_pod}" ]]; then
    kubectl -n "${NAMESPACE}" get --raw "/api/v1/namespaces/${NAMESPACE}/pods/${nats_pod}:8222/proxy/healthz" || true
    kubectl -n "${NAMESPACE}" get --raw "/api/v1/namespaces/${NAMESPACE}/pods/${nats_pod}:8222/proxy/varz" || true
    kubectl -n "${NAMESPACE}" get --raw "/api/v1/namespaces/${NAMESPACE}/pods/${nats_pod}:8222/proxy/jsz?accounts=true&streams=true&consumers=true" || true
  else
    echo "nats pod not found" >&2
  fi

  # 主要 deploy のログ（必要に応じて増やす）
  for d in gateway keycloak account entitlement matchmaking notification nats; do
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
  if [[ -n "${ENTITLEMENT_PF_PID:-}" ]]; then
    kill "${ENTITLEMENT_PF_PID}" >/dev/null 2>&1 || true
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
    tail -n 40 /tmp/pf.log >&2 || true
    echo "--- keycloak port-forward log ---" >&2
    tail -n 40 /tmp/pf-keycloak.log >&2 || true
    echo "--- entitlement port-forward log ---" >&2
    tail -n 40 /tmp/pf-entitlement.log >&2 || true
    dump_diagnostics
    exit 1
  fi
}

echo "=== Port-forward svc/${GATEWAY_SERVICE} ${LOCAL_PORT}:${REMOTE_PORT} (ns=${GATEWAY_NAMESPACE}) ==="
kubectl -n "${GATEWAY_NAMESPACE}" port-forward "svc/${GATEWAY_SERVICE}" "${LOCAL_PORT}:${REMOTE_PORT}" >/tmp/pf.log 2>&1 &
GATEWAY_PF_PID=$!

echo "=== Port-forward svc/${KEYCLOAK_SERVICE} ${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${KEYCLOAK_SERVICE}" "${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_REMOTE_PORT}" >/tmp/pf-keycloak.log 2>&1 &
KEYCLOAK_PF_PID=$!

echo "=== Port-forward svc/${ENTITLEMENT_SERVICE} ${ENTITLEMENT_LOCAL_PORT}:${ENTITLEMENT_REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${ENTITLEMENT_SERVICE}" "${ENTITLEMENT_LOCAL_PORT}:${ENTITLEMENT_REMOTE_PORT}" >/tmp/pf-entitlement.log 2>&1 &
ENTITLEMENT_PF_PID=$!

run_test_script \
  "health" \
  "${SCRIPT_DIR}/tests/health.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-well-known-url "${KEYCLOAK_WELL_KNOWN_URL}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "entitlement-notification-pipeline" \
  "${SCRIPT_DIR}/tests/entitlement-notification-pipeline.sh" \
  --entitlement-base-url "${ENTITLEMENT_BASE_URL}" \
  --namespace "${NAMESPACE}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "notification-delivery-dlq" \
  "${SCRIPT_DIR}/tests/notification-delivery-dlq.sh" \
  --entitlement-base-url "${ENTITLEMENT_BASE_URL}" \
  --namespace "${NAMESPACE}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "mesh-authz-non-gateway-denied" \
  "${SCRIPT_DIR}/tests/mesh-authz-non-gateway-denied.sh" \
  --namespace "${NAMESPACE}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "external-entitlement-payment-api" \
  "${SCRIPT_DIR}/tests/external-entitlement-payment-api.sh" \
  --base-url "${BASE_URL}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "external-entitlement-non-payment-denied" \
  "${SCRIPT_DIR}/tests/external-entitlement-non-payment-denied.sh" \
  --base-url "${BASE_URL}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "oidc-user-ownership" \
  "${SCRIPT_DIR}/tests/oidc-user-ownership.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}"

run_test_script \
  "oidc-other-user-forbidden" \
  "${SCRIPT_DIR}/tests/oidc-other-user-forbidden.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}"

run_test_script \
  "oidc-profile-aggregate" \
  "${SCRIPT_DIR}/tests/oidc-profile-aggregate.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}" \
  --second-username "${SECOND_USERNAME}" \
  --second-password "${SECOND_PASSWORD}"

run_test_script \
  "matchmaking-join-idempotency" \
  "${SCRIPT_DIR}/tests/matchmaking-join-idempotency.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}"

run_test_script \
  "matchmaking-cancel-idempotency" \
  "${SCRIPT_DIR}/tests/matchmaking-cancel-idempotency.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}"

run_test_script \
  "matchmaking-ttl-expired" \
  "${SCRIPT_DIR}/tests/matchmaking-ttl-expired.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}" \
  --timeout-sec "${TIMEOUT_SEC}"

run_test_script \
  "matchmaking-ticket-ownership" \
  "${SCRIPT_DIR}/tests/matchmaking-ticket-ownership.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --realm "${REALM}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}" \
  --second-username "${SECOND_USERNAME}" \
  --second-password "${SECOND_PASSWORD}"

run_test_script \
  "matchmaking-match-notification-pipeline" \
  "${SCRIPT_DIR}/tests/matchmaking-match-notification-pipeline.sh" \
  --base-url "${BASE_URL}" \
  --keycloak-base-url "${KEYCLOAK_BASE_URL}" \
  --realm "${REALM}" \
  --username "${USERNAME}" \
  --password "${PASSWORD}" \
  --second-username "${SECOND_USERNAME}" \
  --second-password "${SECOND_PASSWORD}" \
  --namespace "${NAMESPACE}" \
  --timeout-sec "${TIMEOUT_SEC}"

echo "=== All E2E tests passed ==="
