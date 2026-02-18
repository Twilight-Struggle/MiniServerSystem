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
require_cmd awk
require_cmd sed
require_cmd tr

COOKIE_JAR="$(mktemp)"
LOGIN_HEADERS="$(mktemp)"
AUTH_HEADERS="$(mktemp)"
LOGIN_PAGE="$(mktemp)"
USER_BODY_FILE="$(mktemp)"
cleanup() {
  rm -f "${COOKIE_JAR}" "${LOGIN_HEADERS}" "${AUTH_HEADERS}" "${LOGIN_PAGE}" "${USER_BODY_FILE}" || true
}
trap cleanup EXIT

echo "=== OIDC login flow ==="
curl -fsS -D "${LOGIN_HEADERS}" -o /dev/null "${BASE_URL}/login"
LOGIN_LOCATION="$(extract_header_value "${LOGIN_HEADERS}" "Location")"
if [[ -z "${LOGIN_LOCATION}" ]]; then
  echo "ERROR: /login did not return Location header" >&2
  exit 1
fi
LOGIN_LOCATION="$(absolute_url_from_base "${BASE_URL}" "${LOGIN_LOCATION}")"

curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" -D "${AUTH_HEADERS}" -o /dev/null "${LOGIN_LOCATION}"
AUTH_LOCATION="$(extract_header_value "${AUTH_HEADERS}" "Location")"
if [[ -z "${AUTH_LOCATION}" ]]; then
  echo "ERROR: /oauth2/authorization/keycloak did not return provider redirect" >&2
  exit 1
fi
AUTH_LOCATION_LOCAL="$(replace_origin "${AUTH_LOCATION}" "${KEYCLOAK_BASE_URL}")"

curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${AUTH_LOCATION_LOCAL}" -o "${LOGIN_PAGE}"
FORM_ACTION="$(
  tr '\n' ' ' <"${LOGIN_PAGE}" \
    | sed -n 's/.*id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p'
)"
if [[ -z "${FORM_ACTION}" ]]; then
  echo "ERROR: could not find Keycloak login form action" >&2
  exit 1
fi
FORM_ACTION="${FORM_ACTION//&amp;/&}"
FORM_ACTION_LOCAL="$(absolute_url_from_base "${KEYCLOAK_BASE_URL}" "${FORM_ACTION}")"

curl -fsS -L -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -X POST "${FORM_ACTION_LOCAL}" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  --data "credentialId=" \
  -o /dev/null

echo "=== Verify authenticated subject identity via BFF -> Account ==="
ME_JSON="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE_URL}/v1/me")"
MY_USER_ID="$(extract_json_field "${ME_JSON}" "userId")"
if [[ -z "${MY_USER_ID}" ]]; then
  echo "ERROR: /v1/me did not return userId" >&2
  echo "response=${ME_JSON}" >&2
  exit 1
fi

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
