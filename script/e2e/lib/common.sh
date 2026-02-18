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
