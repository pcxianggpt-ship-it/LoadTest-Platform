#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/loadtest-platform}"
BACKEND_HOME="${BACKEND_HOME:-${APP_HOME}/backend}"
DATA_DIR="${DATA_DIR:-${APP_HOME}/data}"
LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
JAR_PATH="${JAR_PATH:-${BACKEND_HOME}/loadtest-platform.jar}"
BUNDLED_JAVA="${APP_HOME}/runtime/bin/java"

mkdir -p "${DATA_DIR}" "${LOG_DIR}"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:sqlite:${DATA_DIR}/loadtest-platform.db}"
export SERVER_PORT="${SERVER_PORT:-8080}"

if [[ ! -x "${BUNDLED_JAVA}" ]]; then
  echo "Bundled Java runtime not found: ${BUNDLED_JAVA}" >&2
  exit 1
fi

exec "${BUNDLED_JAVA}" ${JAVA_OPTS:-} -jar "${JAR_PATH}"
