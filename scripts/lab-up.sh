#!/usr/bin/env bash
#
# lab-up.sh — build and start the JMX/RMI security laboratory.
#
# Usage:
#   ./scripts/lab-up.sh --mode hardened                 (default: hardened)
#   ./scripts/lab-up.sh --mode vulnerable --ack-insecure-lab
#
# Vulnerable mode is an explicit opt-in: without --ack-insecure-lab the
# script refuses to start an unauthenticated, unencrypted JMX endpoint.
#
# The lab binds only to an internal Docker network; no JMX/RMI port is ever
# published to the host.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAB_DIR="${REPO_ROOT}/lab"
COMPOSE="docker compose -f ${LAB_DIR}/docker-compose.yml"

MODE="hardened"
ACK=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --ack-insecure-lab) ACK=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ "${MODE}" != "hardened" && "${MODE}" != "vulnerable" ]]; then
  echo "error: --mode must be 'hardened' or 'vulnerable'" >&2
  exit 2
fi

if [[ "${MODE}" == "vulnerable" && "${ACK}" -ne 1 ]]; then
  echo "error: vulnerable mode is an explicit opt-in." >&2
  echo "       re-run with: --mode vulnerable --ack-insecure-lab" >&2
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

echo "[lab-up] mode=${MODE}"

# Generate secrets (idempotent: regenerate only if missing).
if [[ ! -f "${LAB_DIR}/.secrets/lab.env" ]]; then
  echo "[lab-up] generating secrets..."
  bash "${REPO_ROOT}/scripts/generate-secrets.sh"
fi

# Load password variables for the compose environment.
# shellcheck disable=SC1091
source "${LAB_DIR}/.secrets/lab.env"

# Write the per-mode env file consumed by compose (--env-file).
MODE_ENV_FILE="${LAB_DIR}/.secrets/compose.${MODE}.env"
if [[ "${MODE}" == "hardened" ]]; then
  {
    echo "LAB_MODE=hardened"
    echo "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.keyStore=/secrets/server.p12 -Djavax.net.ssl.keyStorePassword=${KEY_PASS} -Djavax.net.ssl.trustStore=/secrets/server-trust.p12 -Djavax.net.ssl.trustStorePassword=${TRUST_PASS} -Djavax.net.ssl.needClientAuth=true -Dlab.password.file=/secrets/jmx.password -Dlab.access.file=/secrets/jmx.access"
  } > "${MODE_ENV_FILE}"
else
  {
    echo "LAB_MODE=vulnerable"
    echo "JAVA_TOOL_OPTIONS="
  } > "${MODE_ENV_FILE}"
fi

echo "[lab-up] building images..."
${COMPOSE} --env-file "${MODE_ENV_FILE}" build --pull target verifier

echo "[lab-up] starting target..."
${COMPOSE} --env-file "${MODE_ENV_FILE}" up -d target

echo "[lab-up] waiting for target readiness..."
for _ in $(seq 1 30); do
  if ${COMPOSE} logs target 2>/dev/null | grep -q "JMX connector listening"; then
    break
  fi
  sleep 1
done

if ! ${COMPOSE} logs target 2>/dev/null | grep -q "JMX connector listening"; then
  echo "[lab-up] target did not become ready; last logs:" >&2
  ${COMPOSE} logs target >&2 || true
  exit 1
fi

echo "[lab-up] ready. Target is reachable only inside the lab network."
echo "         Verify with: ./scripts/verify.sh --mode ${MODE}"
