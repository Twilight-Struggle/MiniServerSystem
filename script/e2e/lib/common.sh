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

decode_html_entities() {
  printf '%s' "$1" \
    | sed \
      -e 's/&amp;/\&/g' \
      -e 's/&#38;/\&/g' \
      -e 's/&#x26;/\&/g' \
      -e 's/&#x2F;/\//g' \
      -e 's/&#47;/\//g' \
      -e 's/&#61;/=/g' \
      -e 's/&#x3D;/=/g' \
      -e 's/&#63;/?/g' \
      -e 's/&#x3F;/?/g' \
      -e 's/&#58;/:/g' \
      -e 's/&#x3A;/:/g' \
      -e 's/&quot;/"/g' \
      -e 's/&#34;/"/g' \
      -e 's/&#x22;/"/g' \
      -e "s/&#39;/'/g" \
      -e "s/&#x27;/'/g" \
      -e 's/&lt;/</g' \
      -e 's/&gt;/>/g'
}

build_cookie_header_from_jar() {
  local jar_file="$1"
  awk 'BEGIN{sep=""} /^[^#]/ && NF>=7 {printf "%s%s=%s", sep, $6, $7; sep="; "} END {print ""}' "${jar_file}"
}

build_cookie_header_from_headers() {
  local headers_file="$1"
  awk '
    BEGIN{sep=""}
    /^Set-Cookie:/ {
      line=$0
      sub(/^Set-Cookie:[[:space:]]*/, "", line)
      n=split(line, parts, ";")
      if (n >= 1 && parts[1] ~ /=/) {
        printf "%s%s", sep, parts[1]
        sep="; "
      }
    }
    END {print ""}
  ' "${headers_file}"
}

oidc_login() {
  local base_url="$1"
  local keycloak_base_url="$2"
  local username="$3"
  local password="$4"
  local cookie_jar="$5"
  local keycloak_cookie_jar login_headers auth_headers auth_page_headers login_page post_headers post_body
  local keycloak_cookie_header
  local keycloak_authority keycloak_host keycloak_port
  local -a keycloak_resolve_args

  keycloak_authority="${keycloak_base_url#http://}"
  keycloak_authority="${keycloak_authority#https://}"
  keycloak_authority="${keycloak_authority%%/*}"
  keycloak_host="${keycloak_authority%%:*}"
  keycloak_port="${keycloak_authority##*:}"
  keycloak_resolve_args=()
  if [[ "${keycloak_host}" != "127.0.0.1" && "${keycloak_host}" != "localhost" ]]; then
    keycloak_resolve_args=(--resolve "${keycloak_host}:${keycloak_port}:127.0.0.1")
  fi

  keycloak_cookie_jar="$(mktemp)"
  login_headers="$(mktemp)"
  auth_headers="$(mktemp)"
  auth_page_headers="$(mktemp)"
  login_page="$(mktemp)"
  post_headers="$(mktemp)"
  post_body="$(mktemp)"
  local form_action_raw form_action_local

  local status
  status="$(
    curl -sS -o /dev/null -D "${login_headers}" -w "%{http_code}" \
      -c "${cookie_jar}" -b "${cookie_jar}" \
      "${base_url}/login"
  )"
  if [[ "${status}" != "302" ]]; then
    echo "ERROR: /login returned unexpected status=${status}" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi

  local login_location
  login_location="$(extract_header_value "${login_headers}" "Location")"
  if [[ -z "${login_location}" ]]; then
    echo "ERROR: /login did not return Location header" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi
  login_location="$(absolute_url_from_base "${base_url}" "${login_location}")"

  status="$(
    curl -sS -o /dev/null -D "${auth_headers}" -w "%{http_code}" \
      -c "${cookie_jar}" -b "${cookie_jar}" \
      "${login_location}"
  )"
  if [[ "${status}" != "302" ]]; then
    echo "ERROR: oauth2 authorization start returned unexpected status=${status}" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi

  local auth_location
  auth_location="$(extract_header_value "${auth_headers}" "Location")"
  if [[ -z "${auth_location}" ]]; then
    echo "ERROR: /oauth2/authorization/keycloak did not return provider redirect" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi
  local auth_location_local
  auth_location_local="$(replace_origin "${auth_location}" "${keycloak_base_url}")"

  status="$(
    curl -sS -o "${login_page}" -D "${auth_page_headers}" -w "%{http_code}" \
      "${keycloak_resolve_args[@]}" \
      -c "${keycloak_cookie_jar}" -b "${keycloak_cookie_jar}" \
      "${auth_location_local}"
  )"
  if [[ "${status}" != "200" ]]; then
    echo "ERROR: Keycloak authorization page returned unexpected status=${status}" >&2
    echo "response=$(tr '\n' ' ' < "${login_page}" | head -c 500)" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi
  keycloak_cookie_header="$(build_cookie_header_from_jar "${keycloak_cookie_jar}")"
  if [[ -z "${keycloak_cookie_header}" ]]; then
    keycloak_cookie_header="$(build_cookie_header_from_headers "${auth_page_headers}")"
  fi

  form_action_raw="$(
    perl -0777 -ne 'if (/<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"/s) { print $1; }' "${login_page}"
  )"
  if [[ -z "${form_action_raw}" ]]; then
    echo "ERROR: could not find Keycloak login form action" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi
  form_action_raw="$(decode_html_entities "${form_action_raw}")"
  form_action_local="$(replace_origin "${form_action_raw}" "${keycloak_base_url}")"
  form_action_local="$(absolute_url_from_base "${keycloak_base_url}" "${form_action_local}")"

  status="$(
    curl -sS -o "${post_body}" -D "${post_headers}" -w "%{http_code}" \
    "${keycloak_resolve_args[@]}" \
    -c "${keycloak_cookie_jar}" -b "${keycloak_cookie_jar}" \
    -H "Cookie: ${keycloak_cookie_header}" \
    -H "Referer: ${auth_location_local}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -X POST "${form_action_local}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode "credentialId=" \
    --data-urlencode "login=Sign in"
  )"
  if [[ "${status}" != "302" && "${status}" != "303" ]]; then
    echo "ERROR: Keycloak login submit returned unexpected status=${status}" >&2
    echo "response=$(tr '\n' ' ' < "${post_body}" | head -c 500)" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi

  local callback_location
  callback_location="$(extract_header_value "${post_headers}" "Location")"
  if [[ -z "${callback_location}" ]]; then
    echo "ERROR: Keycloak login submit did not return callback Location header" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi
  local callback_location_local
  callback_location_local="$(replace_origin "${callback_location}" "${base_url}")"

  status="$(
    curl -sS -o /dev/null -w "%{http_code}" -L \
      -c "${cookie_jar}" -b "${cookie_jar}" \
      "${callback_location_local}"
  )"
  if [[ "${status}" -ge 400 ]]; then
    echo "ERROR: OIDC callback handling returned status=${status}" >&2
    rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
    return 1
  fi

  rm -f "${keycloak_cookie_jar}" "${login_headers}" "${auth_headers}" "${auth_page_headers}" "${login_page}" "${post_headers}" "${post_body}" || true
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
