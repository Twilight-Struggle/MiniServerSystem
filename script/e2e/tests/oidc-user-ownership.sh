#!/usr/bin/env bash
set -euo pipefail

# Where: script/e2e/tests/oidc-user-ownership.sh
# What: OIDC login E2E and identity-preserving /v1/users/{myUserId} verification.
# Why: Ensure BFF-to-Account call path does not break authenticated user ownership.

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
USER_BODY_FILE="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${USER_BODY_FILE}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
oidc_login "${BASE_URL}" "${KEYCLOAK_BASE_URL}" "${USERNAME}" "${PASSWORD}" "${COOKIE_JAR}"

echo "=== Verify authenticated subject identity via BFF -> Account ==="
MY_USER_ID="$(fetch_me_user_id "${BASE_URL}" "${COOKIE_JAR}")"

GET_USER_CODE="$(
  curl -sS -o "${USER_BODY_FILE}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    "${BASE_URL}/v1/users/${MY_USER_ID}"
)"
if [[ "${GET_USER_CODE}" != "200" ]]; then
  echo "ERROR: GET /v1/users/${MY_USER_ID} returned ${GET_USER_CODE}" >&2
  echo "response=$(cat "${USER_BODY_FILE}")" >&2
  exit 1
fi

USER_JSON="$(cat "${USER_BODY_FILE}")"
RETURNED_USER_ID="$(extract_json_field "${USER_JSON}" "userId")"
if [[ "${RETURNED_USER_ID}" != "${MY_USER_ID}" ]]; then
  echo "ERROR: userId mismatch (expected=${MY_USER_ID}, actual=${RETURNED_USER_ID})" >&2
  echo "response=${USER_JSON}" >&2
  exit 1
fi

echo "=== oidc-user-ownership test passed ==="
