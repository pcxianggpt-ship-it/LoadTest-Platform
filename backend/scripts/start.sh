#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/loadtest-platform}"
BACKEND_HOME="${BACKEND_HOME:-${APP_HOME}/backend}"
DATA_DIR="${DATA_DIR:-${APP_HOME}/data}"
LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
JAR_PATH="${JAR_PATH:-${BACKEND_HOME}/loadtest-platform.jar}"

mkdir -p "${DATA_DIR}" "${LOG_DIR}"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:sqlite:${DATA_DIR}/loadtest-platform.db}"
export SERVER_PORT="${SERVER_PORT:-8080}"

exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}"
