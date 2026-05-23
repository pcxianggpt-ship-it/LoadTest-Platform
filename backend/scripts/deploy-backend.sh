#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/loadtest-platform}"
SERVICE_NAME="${SERVICE_NAME:-loadtest-platform}"
APP_USER="${APP_USER:-loadtest}"
APP_GROUP="${APP_GROUP:-${APP_USER}}"
SERVER_PORT="${SERVER_PORT:-8080}"
SKIP_TESTS="${SKIP_TESTS:-false}"
JAVA_HOME="${JAVA_HOME:-/opt/jdk-17.0.17}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"

DEPLOY_BACKEND_DIR="${APP_HOME}/backend"
DEPLOY_DATA_DIR="${APP_HOME}/data"
DEPLOY_LOG_DIR="${APP_HOME}/logs"
DEPLOY_JAR="${DEPLOY_BACKEND_DIR}/loadtest-platform.jar"
DEPLOY_START_SH="${DEPLOY_BACKEND_DIR}/start.sh"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
SOURCE_SERVICE_FILE="${SCRIPT_DIR}/loadtest-platform.service"
SOURCE_START_SH="${SCRIPT_DIR}/start.sh"

run_as_root() {
  if [[ "${EUID}" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    echo "Root permission is required to run: $*" >&2
    exit 1
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

ensure_user() {
  if id "${APP_USER}" >/dev/null 2>&1; then
    return
  fi
  run_as_root useradd --system --create-home --shell /sbin/nologin "${APP_USER}"
}

build_backend() {
  require_command mvn
  cd "${BACKEND_DIR}"
  if [[ "${SKIP_TESTS}" == "true" ]]; then
    mvn clean package -DskipTests
  else
    mvn clean package
  fi
}

latest_jar() {
  local jar
  jar="$(find "${BACKEND_DIR}/target" -maxdepth 1 -type f -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | sort | tail -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "Build artifact not found: ${BACKEND_DIR}/target/*.jar" >&2
    exit 1
  fi
  printf '%s\n' "${jar}"
}

backup_database() {
  local db_path="${DEPLOY_DATA_DIR}/loadtest-platform.db"
  if [[ -f "${db_path}" ]]; then
    local backup_path="${db_path}.$(date +%Y%m%d%H%M%S).bak"
    run_as_root cp "${db_path}" "${backup_path}"
    echo "Database backup created: ${backup_path}"
  fi
}

install_service_file() {
  if [[ ! -f "${SOURCE_SERVICE_FILE}" ]]; then
    echo "Service template not found: ${SOURCE_SERVICE_FILE}" >&2
    exit 1
  fi
  local tmp_file
  tmp_file="$(mktemp)"
  sed \
    -e "s|^User=.*|User=${APP_USER}|" \
    -e "s|^Group=.*|Group=${APP_GROUP}|" \
    -e "s|^WorkingDirectory=.*|WorkingDirectory=${DEPLOY_BACKEND_DIR}|" \
    -e "s|^Environment=APP_HOME=.*|Environment=APP_HOME=${APP_HOME}|" \
    -e "s|^Environment=JAVA_HOME=.*|Environment=JAVA_HOME=${JAVA_HOME}|" \
    -e "s|^Environment=SPRING_DATASOURCE_URL=.*|Environment=SPRING_DATASOURCE_URL=jdbc:sqlite:${DEPLOY_DATA_DIR}/loadtest-platform.db|" \
    -e "s|^Environment=SERVER_PORT=.*|Environment=SERVER_PORT=${SERVER_PORT}|" \
    -e "s|^ExecStart=.*|ExecStart=${DEPLOY_START_SH}|" \
    "${SOURCE_SERVICE_FILE}" > "${tmp_file}"
  run_as_root cp "${tmp_file}" "${SERVICE_FILE}"
  rm -f "${tmp_file}"
}

deploy_files() {
  local jar
  jar="$(latest_jar)"

  ensure_user
  run_as_root mkdir -p "${DEPLOY_BACKEND_DIR}" "${DEPLOY_DATA_DIR}" "${DEPLOY_LOG_DIR}"
  backup_database

  run_as_root cp "${jar}" "${DEPLOY_JAR}"
  run_as_root cp "${SOURCE_START_SH}" "${DEPLOY_START_SH}"
  run_as_root chmod +x "${DEPLOY_START_SH}"
  install_service_file
  run_as_root chown -R "${APP_USER}:${APP_GROUP}" "${APP_HOME}"
}

restart_service() {
  require_command systemctl
  if systemctl list-unit-files "${SERVICE_NAME}.service" >/dev/null 2>&1; then
    run_as_root systemctl stop "${SERVICE_NAME}" || true
  fi
  run_as_root systemctl daemon-reload
  run_as_root systemctl enable "${SERVICE_NAME}"
  run_as_root systemctl restart "${SERVICE_NAME}"
  run_as_root systemctl status "${SERVICE_NAME}" --no-pager
}

main() {
  echo "Project root: ${PROJECT_ROOT}"
  echo "Backend dir: ${BACKEND_DIR}"
  echo "Deploy dir: ${APP_HOME}"
  echo "Service name: ${SERVICE_NAME}"
  echo "JAVA_HOME: ${JAVA_HOME}"

  build_backend
  deploy_files
  restart_service

  echo "Backend deployment finished. Health check: http://127.0.0.1:${SERVER_PORT}/health"
}

main "$@"
