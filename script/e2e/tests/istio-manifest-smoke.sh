#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/istio-manifest-smoke.sh
# What: Smoke-test rendered manifests for Istio and NetworkPolicy resources.
# Why: Catch accidental regression in security manifest generation before cluster apply.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CHART_DIR="${ROOT_DIR}/deploy/helm/miniserversystem-platform"
VALUES_FILE="${CHART_DIR}/values-local.yaml"
INFRA_OVERLAY="${ROOT_DIR}/deploy/kustomize/infra/overlays/local"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1" >&2
    exit 1
  fi
}

require_cmd helm
require_cmd kubectl

HELM_RENDERED="$(mktemp)"
KUSTOMIZE_RENDERED="$(mktemp)"
trap 'rm -f "${HELM_RENDERED}" "${KUSTOMIZE_RENDERED}"' EXIT

helm template miniserversystem-platform "${CHART_DIR}" -f "${VALUES_FILE}" > "${HELM_RENDERED}"
kubectl kustomize "${INFRA_OVERLAY}" > "${KUSTOMIZE_RENDERED}"

assert_contains() {
  local file="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "ERROR: expected pattern not found: ${pattern}" >&2
    exit 1
  fi
}

assert_contains "${HELM_RENDERED}" "kind: Gateway"
assert_contains "${HELM_RENDERED}" "name: miniserversystem-gateway"
assert_contains "${HELM_RENDERED}" "kind: PeerAuthentication"
assert_contains "${HELM_RENDERED}" "mode: STRICT"
assert_contains "${HELM_RENDERED}" "name: allow-health-probes"
assert_contains "${HELM_RENDERED}" "kind: DestinationRule"
assert_contains "${HELM_RENDERED}" "name: nats-plaintext"
assert_contains "${HELM_RENDERED}" "name: account-circuit-breaker"
assert_contains "${HELM_RENDERED}" "maxConnections: 100"
assert_contains "${HELM_RENDERED}" "connectTimeout: \"1s\""
assert_contains "${HELM_RENDERED}" "http1MaxPendingRequests: 50"
assert_contains "${HELM_RENDERED}" "maxRequestsPerConnection: 20"
assert_contains "${HELM_RENDERED}" "consecutive5xxErrors: 5"
assert_contains "${HELM_RENDERED}" "baseEjectionTime: \"30s\""
assert_contains "${HELM_RENDERED}" "maxEjectionPercent: 50"
assert_contains "${HELM_RENDERED}" "name: gateway-account-timeout"
assert_contains "${HELM_RENDERED}" "name: gateway-to-account-users-get"
assert_contains "${HELM_RENDERED}" "name: gateway-to-account-resolve-identity"
assert_contains "${HELM_RENDERED}" "name: gateway-to-account-default"
assert_contains "${HELM_RENDERED}" "timeout: \"1s\""
assert_contains "${HELM_RENDERED}" "attempts: 2"
assert_contains "${HELM_RENDERED}" "perTryTimeout: \"300ms\""
assert_contains "${HELM_RENDERED}" "retryOn: \"connect-failure,refused-stream,unavailable,cancelled\""
assert_contains "${HELM_RENDERED}" "exact: GET"
assert_contains "${HELM_RENDERED}" "prefix: /users/"
assert_contains "${HELM_RENDERED}" "exact: POST"
assert_contains "${HELM_RENDERED}" "exact: /identities:resolve"
assert_contains "${HELM_RENDERED}" "name: account-allow-gateway"
assert_contains "${HELM_RENDERED}" "name: entitlement-allow-external-payment"
assert_contains "${HELM_RENDERED}" "name: notification-deny-all-ingress"
assert_contains "${HELM_RENDERED}" "sidecar.istio.io/inject: \"true\""
assert_contains "${HELM_RENDERED}" "traffic.sidecar.istio.io/excludeOutboundPorts: \"4222\""
assert_contains "${HELM_RENDERED}" "exact: \"/v1/entitlements/grants\""
assert_contains "${HELM_RENDERED}" "exact: \"/v1/entitlements/revokes\""
assert_contains "${HELM_RENDERED}" "name: OTEL_SERVICE_NAME"
assert_contains "${HELM_RENDERED}" "value: \"gateway\""
assert_contains "${HELM_RENDERED}" "value: \"account\""
assert_contains "${HELM_RENDERED}" "value: \"entitlement\""
assert_contains "${HELM_RENDERED}" "value: \"notification\""
assert_contains "${HELM_RENDERED}" "name: OTEL_EXPORTER_OTLP_ENDPOINT"
assert_contains "${HELM_RENDERED}" "value: \"http://otel-collector:4317\""
assert_contains "${HELM_RENDERED}" "name: JAVA_TOOL_OPTIONS"
assert_contains "${HELM_RENDERED}" "-javaagent:/otel/opentelemetry-javaagent.jar"

assert_contains "${KUSTOMIZE_RENDERED}" "kind: NetworkPolicy"
assert_contains "${KUSTOMIZE_RENDERED}" "name: postgres-allow-apps-only"
assert_contains "${KUSTOMIZE_RENDERED}" "name: nats-allow-apps-only"
assert_contains "${KUSTOMIZE_RENDERED}" "name: redis-allow-matchmaking-only"
assert_contains "${KUSTOMIZE_RENDERED}" "name: otel-collector-config"
assert_contains "${KUSTOMIZE_RENDERED}" "name: otel-collector"
assert_contains "${KUSTOMIZE_RENDERED}" "name: otlp-grpc"
assert_contains "${KUSTOMIZE_RENDERED}" "name: otlp-http"

echo "OK: Istio/NetworkPolicy manifest smoke test passed"
