#!/usr/bin/env bash
#
# verify.sh — run the security verification scenarios against the lab.
#
# Usage:
#   ./scripts/verify.sh --mode vulnerable
#   ./scripts/verify.sh --mode hardened
#
# Produces PASS/FAIL per scenario and a final exit code (0 = all scenarios
# behaved as expected). Requires the lab to be running (see lab-up.sh).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAB_DIR="${REPO_ROOT}/lab"

MODE="hardened"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ "${MODE}" != "hardened" && "${MODE}" != "vulnerable" ]]; then
  echo "error: --mode must be 'hardened' or 'vulnerable'" >&2
  exit 2
fi

command -v docker >/dev/null 2>&1 || { echo "error: docker not found" >&2; exit 2; }
if docker info >/dev/null 2>&1; then
  DOCKER="docker"
elif sudo -n docker info >/dev/null 2>&1; then
  DOCKER="sudo -n docker"
else
  echo "error: cannot access docker (try sudo, or add your user to the docker group)" >&2
  exit 2
fi
COMPOSE="${DOCKER} compose -f ${LAB_DIR}/docker-compose.yml"

MODE_ENV_FILE="${LAB_DIR}/.secrets/compose.${MODE}.env"

FAILURES=0

# run_scenario <label> <expect_ok> [docker -e flags...] -- [verifier args...]
#   expect_ok=1 -> verifier must exit 0
#   expect_ok=0 -> verifier must exit non-zero (a correctly-denied operation)
run_scenario() {
  local label="$1" expect_ok="$2"
  shift 2
  local -a envflags=()
  while [[ $# -gt 0 && "$1" != "--" ]]; do
    envflags+=("$1"); shift
  done
  [[ $# -gt 0 && "$1" == "--" ]] && shift
  local out
  echo "----- ${label} -----"
  if out="$(${COMPOSE} --env-file "${MODE_ENV_FILE}" run --rm "${envflags[@]}" verifier "$@" 2>&1)"; then
    echo "${out}"
    if [[ "${expect_ok}" == "1" ]]; then
      echo "----- ${label}: ok -----"
    else
      echo "----- ${label}: FAIL (expected denial, verifier succeeded) -----"
      FAILURES=$((FAILURES + 1))
    fi
  else
    echo "${out}"
    if [[ "${expect_ok}" == "0" ]]; then
      echo "----- ${label}: DENIED (ok) -----"
    else
      echo "----- ${label}: FAIL -----"
      FAILURES=$((FAILURES + 1))
    fi
  fi
  echo
}

if [[ "${MODE}" == "vulnerable" ]]; then
  # Unauthenticated, unencrypted access must succeed — this is the finding.
  run_scenario "vulnerable: anonymous connect + enumerate + read" 1 \
    -- --host target --port 1099
  run_scenario "vulnerable: anonymous unauthorized state change" 1 \
    -- --host target --port 1099 --invoke enableMaintenance --expect-change
  run_scenario "vulnerable: anonymous revert (disable)" 1 \
    -- --host target --port 1099 --invoke disableMaintenance --expect-change
else
  # shellcheck disable=SC1091
  source "${LAB_DIR}/.secrets/lab.env"

  CLIENT_TLS=" -Djavax.net.ssl.trustStore=/secrets/client-trust.p12 -Djavax.net.ssl.trustStorePassword=${TRUST_PASS} -Djavax.net.ssl.keyStore=/secrets/client.p12 -Djavax.net.ssl.keyStorePassword=${KEY_PASS}"

  run_scenario "hardened: operator (valid creds + TLS) invokes" 1 \
    -e "JAVA_TOOL_OPTIONS=${CLIENT_TLS}" \
    -- --host target --port 1099 \
    --user operator --password "${OPERATOR_PASS}" --invoke enableMaintenance --expect-change

  run_scenario "hardened: readonly (valid creds) reads, no write" 1 \
    -e "JAVA_TOOL_OPTIONS=${CLIENT_TLS}" \
    -- --host target --port 1099 --user readonly --password "${READONLY_PASS}"

  run_scenario "hardened: readonly attempts state change (must be DENIED)" 0 \
    -e "JAVA_TOOL_OPTIONS=${CLIENT_TLS}" \
    -- --host target --port 1099 --user readonly --password "${READONLY_PASS}" \
    --invoke enableMaintenance

  run_scenario "hardened: invalid credentials rejected" 1 \
    -e "JAVA_TOOL_OPTIONS=${CLIENT_TLS}" \
    -- --host target --port 1099 --user operator --password "not-the-password" --expect-fail

  run_scenario "hardened: anonymous rejected" 1 \
    -e "JAVA_TOOL_OPTIONS=${CLIENT_TLS}" \
    -- --host target --port 1099 --expect-fail

  UNTRUSTED_TLS=" -Djavax.net.ssl.trustStore=/secrets/untrusted-trust.p12 -Djavax.net.ssl.trustStorePassword=${TRUST_PASS} -Djavax.net.ssl.keyStore=/secrets/untrusted-client.p12 -Djavax.net.ssl.keyStorePassword=${KEY_PASS}"
  run_scenario "hardened: untrusted TLS client rejected" 1 \
    -e "JAVA_TOOL_OPTIONS=${UNTRUSTED_TLS}" \
    -- --host target --port 1099 --user operator --password "${OPERATOR_PASS}" --expect-fail
fi

if [[ "${FAILURES}" -eq 0 ]]; then
  echo "ALL VERIFY SCENARIOS PASSED"
  exit 0
else
  echo "${FAILURES} VERIFY SCENARIO(S) FAILED"
  exit 1
fi
