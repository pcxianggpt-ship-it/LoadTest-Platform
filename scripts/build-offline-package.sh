#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-loadtest-platform}"
SKIP_TESTS="${SKIP_TESTS:-false}"
OUTPUT_DIR="${OUTPUT_DIR:-}"
PACKAGE_VERSION="${PACKAGE_VERSION:-$(date +%Y%m%d%H%M%S)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/offline-dist}"
WORK_DIR="${OUTPUT_DIR}/${APP_NAME}-${PACKAGE_VERSION}"
PACKAGE_FILE="${OUTPUT_DIR}/${APP_NAME}-offline-${PACKAGE_VERSION}.tar.gz"

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

latest_backend_jar() {
  local jar
  jar="$(find "${BACKEND_DIR}/target" -maxdepth 1 -type f -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | sort | tail -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "Backend jar not found in ${BACKEND_DIR}/target" >&2
    exit 1
  fi
  printf '%s\n' "${jar}"
}

write_manifest() {
  cat > "${WORK_DIR}/manifest.txt" <<EOF
app=${APP_NAME}
version=${PACKAGE_VERSION}
built_at=$(date -Iseconds)
backend_jar=backend/loadtest-platform.jar
frontend_dir=frontend/
EOF
}

create_checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && sha256sum "$(basename "${PACKAGE_FILE}")" > "$(basename "${PACKAGE_FILE}").sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && shasum -a 256 "$(basename "${PACKAGE_FILE}")" > "$(basename "${PACKAGE_FILE}").sha256")
  else
    echo "sha256 tool not found, checksum skipped"
  fi
}

main() {
  need_command mvn
  need_command node
  need_command npm
  need_command tar

  echo "==> Project root: ${PROJECT_ROOT}"
  echo "==> Output dir: ${OUTPUT_DIR}"

  echo "==> Building backend"
  cd "${BACKEND_DIR}"
  if [[ "${SKIP_TESTS}" == "true" ]]; then
    mvn clean package -DskipTests
  else
    mvn clean package
  fi

  echo "==> Building frontend with same-origin API"
  cd "${FRONTEND_DIR}"
  npm ci
  rm -rf dist
  unset VITE_API_BASE_URL
  npm run build

  if grep -R -E "localhost:8080|/api/api|[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:8080" dist >/dev/null 2>&1; then
    echo "Invalid API URL found in frontend dist. Check VITE_API_BASE_URL and rebuild." >&2
    exit 1
  fi

  echo "==> Assembling offline package"
  rm -rf "${WORK_DIR}"
  mkdir -p "${WORK_DIR}/backend" "${WORK_DIR}/frontend" "${WORK_DIR}/scripts"

  cp "$(latest_backend_jar)" "${WORK_DIR}/backend/loadtest-platform.jar"
  cp "${BACKEND_DIR}/scripts/start.sh" "${WORK_DIR}/backend/start.sh"
  cp "${BACKEND_DIR}/scripts/loadtest-platform.service" "${WORK_DIR}/backend/loadtest-platform.service"
  cp -R "${FRONTEND_DIR}/dist/." "${WORK_DIR}/frontend/"
  cp "${SCRIPT_DIR}/deploy-offline-package.sh" "${WORK_DIR}/scripts/deploy-offline-package.sh"
  chmod +x "${WORK_DIR}/backend/start.sh" "${WORK_DIR}/scripts/deploy-offline-package.sh"
  write_manifest

  rm -f "${PACKAGE_FILE}" "${PACKAGE_FILE}.sha256"
  (cd "${OUTPUT_DIR}" && tar -czf "$(basename "${PACKAGE_FILE}")" "$(basename "${WORK_DIR}")")
  create_checksum

  echo "==> Offline package created:"
  echo "${PACKAGE_FILE}"
  if [[ -f "${PACKAGE_FILE}.sha256" ]]; then
    echo "${PACKAGE_FILE}.sha256"
  fi
}

main "$@"
