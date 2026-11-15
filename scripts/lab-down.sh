#!/usr/bin/env bash
#
# lab-down.sh — stop the lab and remove only its own resources.
#
# Removes the project's containers and network (compose project-scoped).
# Does NOT run destructive global commands (no docker system prune) and does
# not touch unrelated containers, images, volumes, or networks.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAB_DIR="${REPO_ROOT}/lab"

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

echo "[lab-down] stopping project containers and network..."
${COMPOSE} down --remove-orphans

echo "[lab-down] done. (Generated secrets under lab/.secrets are left in place;"
echo "           remove them with: rm -rf ${LAB_DIR}/.secrets)"
