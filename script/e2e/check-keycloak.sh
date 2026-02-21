#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/check-keycloak.sh
# What: Validate Keycloak bootstrap in Kubernetes for CI E2E.
# Why: Fail fast when realm/client/user bootstrap is missing or broken.

NAMESPACE="miniserversystem"
SERVICE="keycloak"
LOCAL_PORT="18081"
REMOTE_PORT="8080"
REALM="miniserversystem"
CLIENT_ID="gateway-bff"
CLIENT_SECRET="changeit"
USERNAME="test"
PASSWORD="test"
TIMEOUT_SEC="180"

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --namespace <ns>      Kubernetes namespace (default: ${NAMESPACE})
  --service <svc>       Keycloak Service name (default: ${SERVICE})
  --local-port <port>   Local port for port-forward (default: ${LOCAL_PORT})
  --remote-port <port>  Service port for port-forward (default: ${REMOTE_PORT})
  --realm <name>        Realm name (default: ${REALM})
  --client-id <id>      OIDC client id (default: ${CLIENT_ID})
  --client-secret <s>   OIDC client secret (default: ${CLIENT_SECRET})
  --username <name>     Test user name (default: ${USERNAME})
  --password <pw>       Test user password (default: ${PASSWORD})
  --timeout-sec <sec>   Wait timeout (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --service) SERVICE="$2"; shift 2 ;;
    --local-port) LOCAL_PORT="$2"; shift 2 ;;
    --remote-port) REMOTE_PORT="$2"; shift 2 ;;
    --realm) REALM="$2"; shift 2 ;;
    --client-id) CLIENT_ID="$2"; shift 2 ;;
    --client-secret) CLIENT_SECRET="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 127; }
}

cleanup_pf() {
  if [[ -n "${PF_PID:-}" ]]; then
    kill "${PF_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup_pf EXIT

require_cmd kubectl
require_cmd curl

BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
WELL_KNOWN_URL="${BASE_URL}/realms/${REALM}/.well-known/openid-configuration"
TOKEN_URL="${BASE_URL}/realms/${REALM}/protocol/openid-connect/token"

echo "=== Port-forward svc/${SERVICE} ${LOCAL_PORT}:${REMOTE_PORT} (ns=${NAMESPACE}) ==="
kubectl -n "${NAMESPACE}" port-forward "svc/${SERVICE}" "${LOCAL_PORT}:${REMOTE_PORT}" >/tmp/pf-keycloak.log 2>&1 &
PF_PID=$!

echo "=== Waiting Keycloak well-known endpoint: ${WELL_KNOWN_URL} ==="
end=$((SECONDS + TIMEOUT_SEC))
ok=0
while [[ $SECONDS -lt $end ]]; do
  if curl -fsS "${WELL_KNOWN_URL}" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 2
done

if [[ "${ok}" -ne 1 ]]; then
  echo "ERROR: Keycloak well-known endpoint did not become ready within ${TIMEOUT_SEC}s" >&2
  tail -n 200 /tmp/pf-keycloak.log >&2 || true
  kubectl -n "${NAMESPACE}" get pods -o wide >&2 || true
  kubectl -n "${NAMESPACE}" logs deploy/keycloak --tail=200 >&2 || true
  exit 1
fi

echo "=== Verifying realm/client/user by token grant ==="
TOKEN_RESPONSE="$(
  curl -fsS -X POST "${TOKEN_URL}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}"
)"

if ! grep -q '"access_token"' <<<"${TOKEN_RESPONSE}"; then
  echo "ERROR: token response did not include access_token" >&2
  echo "response=${TOKEN_RESPONSE}" >&2
  exit 1
fi

echo "=== Keycloak bootstrap checks passed ==="
