#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/health.sh
# What: Health-related E2E checks for gateway and Keycloak readiness/liveness.
# Why: Fail fast before running authentication and authorization scenario tests.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_WELL_KNOWN_URL=""
TIMEOUT_SEC="120"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-well-known-url <url> [options]

Options:
  --base-url <url>                 Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-well-known-url <url>  Keycloak well-known endpoint URL
  --timeout-sec <sec>              Wait timeout in seconds (default: ${TIMEOUT_SEC})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-well-known-url) KEYCLOAK_WELL_KNOWN_URL="$2"; shift 2 ;;
    --timeout-sec) TIMEOUT_SEC="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "${BASE_URL}" || -z "${KEYCLOAK_WELL_KNOWN_URL}" ]]; then
  usage
  exit 2
fi

require_cmd curl

if ! wait_for_endpoint "${BASE_URL}/actuator/health/readiness" "${TIMEOUT_SEC}" "gateway readiness"; then
  echo "ERROR: gateway readiness did not become healthy within ${TIMEOUT_SEC}s" >&2
  exit 1
fi

if ! wait_for_endpoint "${KEYCLOAK_WELL_KNOWN_URL}" "${TIMEOUT_SEC}" "keycloak well-known"; then
  echo "ERROR: keycloak well-known did not become ready within ${TIMEOUT_SEC}s" >&2
  exit 1
fi

echo "=== Readiness OK ==="
curl -fsS "${BASE_URL}/actuator/health/readiness" | head -c 400 || true
echo

echo "=== Checking liveness ==="
curl -fsS "${BASE_URL}/actuator/health/liveness" | head -c 400 || true
echo

echo "=== health checks passed ==="
