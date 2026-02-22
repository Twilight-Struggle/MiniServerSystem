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
  if ! grep -Fq "$pattern" "$file"; then
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
assert_contains "${HELM_RENDERED}" "name: account-allow-gateway"
assert_contains "${HELM_RENDERED}" "name: entitlement-allow-external-payment"
assert_contains "${HELM_RENDERED}" "name: notification-deny-all-ingress"
assert_contains "${HELM_RENDERED}" "sidecar.istio.io/inject: \"true\""
assert_contains "${HELM_RENDERED}" "traffic.sidecar.istio.io/excludeOutboundPorts: \"4222\""
assert_contains "${HELM_RENDERED}" "exact: \"/v1/entitlements/grants\""
assert_contains "${HELM_RENDERED}" "exact: \"/v1/entitlements/revokes\""

assert_contains "${KUSTOMIZE_RENDERED}" "kind: NetworkPolicy"
assert_contains "${KUSTOMIZE_RENDERED}" "name: postgres-allow-apps-only"
assert_contains "${KUSTOMIZE_RENDERED}" "name: nats-allow-apps-only"
assert_contains "${KUSTOMIZE_RENDERED}" "name: redis-allow-matchmaking-only"

echo "OK: Istio/NetworkPolicy manifest smoke test passed"
