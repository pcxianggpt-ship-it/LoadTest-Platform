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

resolve_java() {
  if [[ -n "${JAVA_BIN:-}" && -x "${JAVA_BIN}" ]]; then
    printf '%s\n' "${JAVA_BIN}"
    return
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "${JAVA_HOME}/bin/java"
    return
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return
  fi
  local candidate
  for candidate in /opt/jdk-*/bin/java /usr/lib/jvm/*/bin/java; do
    if [[ -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done
  echo "Java executable not found. Set JAVA_HOME or JAVA_BIN." >&2
  exit 1
}

JAVA_EXECUTABLE="$(resolve_java)"
exec "${JAVA_EXECUTABLE}" ${JAVA_OPTS:-} -jar "${JAR_PATH}"
