#!/usr/bin/env bash

# Where: script/e2e/lib/common.sh
# What: Shared shell helpers for E2E scripts.
# Why: Keep each test script small, consistent, and easy to review.

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 127
  }
}

wait_for_endpoint() {
  local endpoint_url="$1"
  local timeout_sec="$2"
  local title="$3"
  echo "=== Waiting for ${title}: ${endpoint_url} (timeout=${timeout_sec}s) ==="
  local end=$((SECONDS + timeout_sec))
  while [[ ${SECONDS} -lt ${end} ]]; do
    if curl -fsS "${endpoint_url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

extract_header_value() {
  local header_file="$1"
  local header_name="$2"
  awk -v name="${header_name}" '
    BEGIN { IGNORECASE = 1 }
    $0 ~ ("^" name ":") {
      sub("^[^:]+:[[:space:]]*", "", $0)
      sub("\r$", "", $0)
      print
      exit
    }' "${header_file}"
}

absolute_url_from_base() {
  local base_url="$1"
  local maybe_relative="$2"
  if [[ "${maybe_relative}" =~ ^https?:// ]]; then
    printf '%s' "${maybe_relative}"
    return
  fi
  if [[ "${maybe_relative}" == /* ]]; then
    printf '%s%s' "${base_url}" "${maybe_relative}"
    return
  fi
  printf '%s/%s' "${base_url}" "${maybe_relative}"
}

replace_origin() {
  local original_url="$1"
  local local_origin="$2"
  local suffix
  suffix="$(printf '%s' "${original_url}" | sed -E 's#^https?://[^/]+##')"
  if [[ "${suffix}" == "${original_url}" ]]; then
    suffix="${original_url}"
  fi
  printf '%s%s' "${local_origin}" "${suffix}"
}

extract_json_field() {
  local json="$1"
  local key="$2"
  printf '%s' "${json}" \
    | tr -d '\n' \
    | sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p"
}

oidc_login() {
  local base_url="$1"
  local keycloak_base_url="$2"
  local username="$3"
  local password="$4"
  local cookie_jar="$5"
  local login_headers auth_headers login_page

  login_headers="$(mktemp)"
  auth_headers="$(mktemp)"
  login_page="$(mktemp)"

  curl -fsS -D "${login_headers}" -o /dev/null "${base_url}/login"
  local login_location
  login_location="$(extract_header_value "${login_headers}" "Location")"
  if [[ -z "${login_location}" ]]; then
    echo "ERROR: /login did not return Location header" >&2
    rm -f "${login_headers}" "${auth_headers}" "${login_page}" || true
    return 1
  fi
  login_location="$(absolute_url_from_base "${base_url}" "${login_location}")"

  curl -fsS -c "${cookie_jar}" -b "${cookie_jar}" -D "${auth_headers}" -o /dev/null "${login_location}"
  local auth_location
  auth_location="$(extract_header_value "${auth_headers}" "Location")"
  if [[ -z "${auth_location}" ]]; then
    echo "ERROR: /oauth2/authorization/keycloak did not return provider redirect" >&2
    rm -f "${login_headers}" "${auth_headers}" "${login_page}" || true
    return 1
  fi
  local auth_location_local
  auth_location_local="$(replace_origin "${auth_location}" "${keycloak_base_url}")"

  curl -fsS -c "${cookie_jar}" -b "${cookie_jar}" "${auth_location_local}" -o "${login_page}"
  local form_action
  form_action="$(
    tr '\n' ' ' <"${login_page}" \
      | sed -n 's/.*id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p'
  )"
  if [[ -z "${form_action}" ]]; then
    echo "ERROR: could not find Keycloak login form action" >&2
    rm -f "${login_headers}" "${auth_headers}" "${login_page}" || true
    return 1
  fi
  form_action="${form_action//&amp;/&}"
  local form_action_local
  form_action_local="$(absolute_url_from_base "${keycloak_base_url}" "${form_action}")"

  curl -fsS -L -c "${cookie_jar}" -b "${cookie_jar}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -X POST "${form_action_local}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data "credentialId=" \
    -o /dev/null

  rm -f "${login_headers}" "${auth_headers}" "${login_page}" || true
}

fetch_me_user_id() {
  local base_url="$1"
  local cookie_jar="$2"
  local me_json
  me_json="$(curl -fsS -c "${cookie_jar}" -b "${cookie_jar}" "${base_url}/v1/me")"
  local my_user_id
  my_user_id="$(extract_json_field "${me_json}" "userId")"
  if [[ -z "${my_user_id}" ]]; then
    echo "ERROR: /v1/me did not return userId" >&2
    echo "response=${me_json}" >&2
    return 1
  fi
  printf '%s' "${my_user_id}"
}
