#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/mesh-authz-non-gateway-denied.sh
# What: Verify non-gateway workload is denied when accessing account/entitlement/matchmaking.
# Why: Ensure Istio AuthorizationPolicy enforces gateway-only service-to-service access.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

NAMESPACE="miniserversystem"
TIMEOUT_SEC="120"
POD_IMAGE="curlimages/curl:8.12.1"
POD_NAME="authz-denied-probe"
POD_SERVICE_ACCOUNT="notification"

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --namespace <ns>            Kubernetes namespace (default: ${NAMESPACE})
  --timeout-sec <sec>         Wait timeout in seconds (default: ${TIMEOUT_SEC})
  --pod-image <image>         Probe pod image (default: ${POD_IMAGE})
  --pod-name <name>           Probe pod name (default: ${POD_NAME})
  --service-account <name>    Probe pod service account (default: ${POD_SERVICE_ACCOUNT})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    --pod-image) POD_IMAGE="$2"; shift 2 ;;
    --pod-name) POD_NAME="$2"; shift 2 ;;
    --service-account) POD_SERVICE_ACCOUNT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

require_cmd kubectl

cleanup() {
  kubectl -n "${NAMESPACE}" delete pod "${POD_NAME}" --ignore-not-found >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== Start probe pod with sidecar (sa=${POD_SERVICE_ACCOUNT}) ==="
kubectl -n "${NAMESPACE}" delete pod "${POD_NAME}" --ignore-not-found >/dev/null 2>&1 || true
kubectl -n "${NAMESPACE}" run "${POD_NAME}" \
  --image="${POD_IMAGE}" \
  --restart=Never \
  --labels="app=e2e-authz-probe" \
  --overrides='{"apiVersion":"v1","spec":{"serviceAccountName":"'"${POD_SERVICE_ACCOUNT}"'"},"metadata":{"annotations":{"sidecar.istio.io/inject":"true"}}}' \
  --command -- sh -c "sleep 600" >/dev/null

kubectl -n "${NAMESPACE}" wait --for=condition=Ready "pod/${POD_NAME}" --timeout="${TIMEOUT_SEC}s" >/dev/null

# On newer Kubernetes, Istio sidecar may appear as restartable initContainer.
proxy_names="$(
  kubectl -n "${NAMESPACE}" get pod "${POD_NAME}" \
    -o jsonpath='{.spec.containers[*].name} {.spec.initContainers[*].name}' \
    | tr -s ' '
)"
if ! printf '%s' "${proxy_names}" | grep -qE '(^| )istio-proxy( |$)'; then
  echo "ERROR: probe pod does not have istio-proxy sidecar; authz verification would be invalid" >&2
  echo "pod=${POD_NAME}" >&2
  kubectl -n "${NAMESPACE}" get pod "${POD_NAME}" -o jsonpath='{.metadata.annotations.sidecar\.istio\.io/status}' >&2 || true
  echo >&2
  kubectl -n "${NAMESPACE}" get pod "${POD_NAME}" -o jsonpath='{.status.containerStatuses[*].name} {.status.initContainerStatuses[*].name}' >&2 || true
  echo >&2
  kubectl -n "${NAMESPACE}" describe pod "${POD_NAME}" >&2 || true
  exit 1
fi

assert_denied() {
  local name="$1"
  local url="$2"
  local status
  status="$(
    kubectl -n "${NAMESPACE}" exec "${POD_NAME}" -- \
      sh -c "curl -sS -o /dev/null -w '%{http_code}' '${url}'" \
      | tr -d '\r\n[:space:]'
  )"
  if [[ "${status}" == "200" ]]; then
    echo "ERROR: expected deny for ${name}, but got status=200 (${url})" >&2
    exit 1
  fi
  echo "Denied as expected: ${name} -> status=${status}"
}

echo "=== Verify non-gateway principal is denied ==="
# /actuator/health/** is intentionally allowed by Istio policy for probes.
# Use non-health paths to verify gateway-only authorization behavior.
assert_denied "account" "http://account:80/users/test-user"
assert_denied "entitlement(non-external-api)" "http://entitlement:80/v1/users/test-user/entitlements"
assert_denied "matchmaking" "http://matchmaking:80/"

echo "=== mesh-authz-non-gateway-denied test passed ==="
