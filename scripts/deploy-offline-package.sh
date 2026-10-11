#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/loadtest-platform}"
SERVICE_NAME="${SERVICE_NAME:-loadtest-platform}"
APP_USER="${APP_USER:-loadtest}"
APP_GROUP="${APP_GROUP:-${APP_USER}}"
SERVER_PORT="${SERVER_PORT:-8080}"
JAVA_HOME="${JAVA_HOME:-/opt/jdk-17.0.17}"
BACKUP_DATABASE="${BACKUP_DATABASE:-true}"

PACKAGE_FILE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DEPLOY_BACKEND_DIR="${APP_HOME}/backend"
DEPLOY_FRONTEND_DIR="${APP_HOME}/frontend"
DEPLOY_DATA_DIR="${APP_HOME}/data"
DEPLOY_LOG_DIR="${APP_HOME}/logs"
DEPLOY_JAR="${DEPLOY_BACKEND_DIR}/loadtest-platform.jar"
DEPLOY_START_SH="${DEPLOY_BACKEND_DIR}/start.sh"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

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

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

resolve_package() {
  if [[ -n "${PACKAGE_FILE}" ]]; then
    if [[ ! -f "${PACKAGE_FILE}" ]]; then
      echo "Package not found: ${PACKAGE_FILE}" >&2
      exit 1
    fi
    return
  fi

  PACKAGE_FILE="$(find "${SCRIPT_DIR}" -maxdepth 1 -type f -name "loadtest-platform-offline-*.tar.gz" | sort | tail -n 1)"
  if [[ -z "${PACKAGE_FILE}" ]]; then
    PACKAGE_FILE="$(find "$(pwd)" -maxdepth 1 -type f -name "loadtest-platform-offline-*.tar.gz" | sort | tail -n 1)"
  fi
  if [[ -z "${PACKAGE_FILE}" ]]; then
    echo "Usage: $0 /path/to/loadtest-platform-offline-*.tar.gz" >&2
    exit 1
  fi
}

ensure_user() {
  if id "${APP_USER}" >/dev/null 2>&1; then
    return
  fi
  run_as_root useradd --system --create-home --shell /sbin/nologin "${APP_USER}"
}

backup_database() {
  local db_path="${DEPLOY_DATA_DIR}/loadtest-platform.db"
  if [[ "${BACKUP_DATABASE}" != "true" || ! -f "${db_path}" ]]; then
    return
  fi
  local backup_path="${db_path}.$(date +%Y%m%d%H%M%S).bak"
  run_as_root cp "${db_path}" "${backup_path}"
  echo "Database backup created: ${backup_path}"
}

extract_package() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  tar -xzf "${PACKAGE_FILE}" -C "${tmp_dir}"
  local package_root
  package_root="$(find "${tmp_dir}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1)"
  if [[ -z "${package_root}" ]]; then
    echo "Invalid package: no root directory found" >&2
    exit 1
  fi
  if [[ ! -f "${package_root}/backend/loadtest-platform.jar" || ! -f "${package_root}/backend/start.sh" || ! -f "${package_root}/frontend/index.html" ]]; then
    echo "Invalid package: required backend or frontend files are missing" >&2
    exit 1
  fi
  printf '%s\n' "${package_root}"
}

install_service_file() {
  local source_service_file="$1/backend/loadtest-platform.service"
  if [[ ! -f "${source_service_file}" ]]; then
    echo "Service template not found: ${source_service_file}" >&2
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
    "${source_service_file}" > "${tmp_file}"
  run_as_root cp "${tmp_file}" "${SERVICE_FILE}"
  rm -f "${tmp_file}"
}

publish_files() {
  local package_root="$1"

  ensure_user
  run_as_root mkdir -p "${DEPLOY_BACKEND_DIR}" "${DEPLOY_FRONTEND_DIR}" "${DEPLOY_DATA_DIR}" "${DEPLOY_LOG_DIR}"
  backup_database

  run_as_root cp "${package_root}/backend/loadtest-platform.jar" "${DEPLOY_JAR}"
  run_as_root cp "${package_root}/backend/start.sh" "${DEPLOY_START_SH}"
  run_as_root chmod +x "${DEPLOY_START_SH}"
  install_service_file "${package_root}"

  if command -v rsync >/dev/null 2>&1; then
    run_as_root rsync -a --delete "${package_root}/frontend/" "${DEPLOY_FRONTEND_DIR}/"
  else
    run_as_root rm -rf "${DEPLOY_FRONTEND_DIR:?}/"*
    run_as_root cp -R "${package_root}/frontend/." "${DEPLOY_FRONTEND_DIR}/"
  fi

  run_as_root chown -R "${APP_USER}:${APP_GROUP}" "${APP_HOME}"
}

restart_backend() {
  need_command systemctl
  run_as_root systemctl daemon-reload
  run_as_root systemctl enable "${SERVICE_NAME}"
  run_as_root systemctl restart "${SERVICE_NAME}"
  run_as_root systemctl status "${SERVICE_NAME}" --no-pager
}

main() {
  need_command tar
  resolve_package

  echo "==> Offline package: ${PACKAGE_FILE}"
  echo "==> Deploy dir: ${APP_HOME}"
  echo "==> Service name: ${SERVICE_NAME}"
  echo "==> Server port: ${SERVER_PORT}"

  local package_root
  package_root="$(extract_package)"
  publish_files "${package_root}"
  restart_backend

  echo "==> Offline deployment finished"
  echo "Backend health check: http://127.0.0.1:${SERVER_PORT}/health"
  echo "Frontend URL: http://<server-ip>:${SERVER_PORT}/"
}

main "$@"
