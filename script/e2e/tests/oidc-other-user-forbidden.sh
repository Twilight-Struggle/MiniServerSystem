#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/oidc-other-user-forbidden.sh
# What: Verify authenticated user cannot read another user's profile via BFF.
# Why: Prevent horizontal privilege escalation even when traffic is routed through BFF.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

BASE_URL=""
KEYCLOAK_BASE_URL=""
USERNAME="test"
PASSWORD="test"

usage() {
  cat <<EOF
Usage: $0 --base-url <url> --keycloak-base-url <url> [options]

Options:
  --base-url <url>          Gateway base URL (example: http://127.0.0.1:18080)
  --keycloak-base-url <url> Keycloak base URL (example: http://127.0.0.1:18081)
  --username <name>         Keycloak test username (default: ${USERNAME})
  --password <pw>           Keycloak test password (default: ${PASSWORD})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --keycloak-base-url) KEYCLOAK_BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "${BASE_URL}" || -z "${KEYCLOAK_BASE_URL}" ]]; then
  usage
  exit 2
fi

require_cmd curl
require_cmd perl

COOKIE_JAR="$(mktemp)"
RESPONSE_BODY_FILE="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${RESPONSE_BODY_FILE}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_JAR}"

echo "=== Verify other user lookup is rejected ==="
MY_USER_ID="$(fetch_me_user_id "${BASE_URL}" "${COOKIE_JAR}")"
OTHER_USER_ID="other-${MY_USER_ID}"

STATUS_CODE="$(
  curl -sS -o "${RESPONSE_BODY_FILE}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    "${BASE_URL}/v1/users/${OTHER_USER_ID}"
)"

if [[ "${STATUS_CODE}" != "403" && "${STATUS_CODE}" != "404" ]]; then
  echo "ERROR: expected 403 or 404 for other user lookup, but got ${STATUS_CODE}" >&2
  echo "response=$(cat "${RESPONSE_BODY_FILE}")" >&2
  exit 1
fi

echo "=== oidc-other-user-forbidden test passed (status=${STATUS_CODE}) ==="
