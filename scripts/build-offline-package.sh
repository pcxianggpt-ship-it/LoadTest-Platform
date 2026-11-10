#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-loadtest-platform}"
SKIP_TESTS="${SKIP_TESTS:-false}"
OUTPUT_DIR="${OUTPUT_DIR:-}"
PACKAGE_VERSION="${PACKAGE_VERSION:-$(date +%Y%m%d%H%M%S)}"
RUNTIME_SOURCE="${RUNTIME_SOURCE:-}"
RUNTIME_ARCHIVE="${RUNTIME_ARCHIVE:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
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

resolve_runtime_dir() {
  if [[ -n "${RUNTIME_SOURCE}" ]]; then
    if [[ ! -x "${RUNTIME_SOURCE}/bin/java" ]]; then
      echo "Invalid RUNTIME_SOURCE, bin/java not found: ${RUNTIME_SOURCE}" >&2
      exit 1
    fi
    printf '%s\n' "${RUNTIME_SOURCE}"
    return
  fi

  if [[ -z "${RUNTIME_ARCHIVE}" || ! -f "${RUNTIME_ARCHIVE}" ]]; then
    echo "Linux Java 17 runtime is required." >&2
    echo "Set RUNTIME_SOURCE to an extracted runtime directory, or RUNTIME_ARCHIVE to a .tar.gz archive." >&2
    exit 1
  fi

  local extract_dir runtime_dir
  extract_dir="$(mktemp -d "${OUTPUT_DIR}/.runtime-${PACKAGE_VERSION}-XXXXXX")"
  tar -xzf "${RUNTIME_ARCHIVE}" -C "${extract_dir}"
  runtime_dir="$(find "${extract_dir}" -type f -path '*/bin/java' -print | sort | head -n 1)"
  if [[ -z "${runtime_dir}" ]]; then
    echo "Invalid RUNTIME_ARCHIVE, bin/java not found: ${RUNTIME_ARCHIVE}" >&2
    exit 1
  fi
  dirname "$(dirname "${runtime_dir}")"
}

validate_runtime() {
  local runtime_dir="$1" release_file java_version
  release_file="${runtime_dir}/release"
  if [[ ! -f "${release_file}" ]]; then
    echo "Runtime release file not found: ${release_file}" >&2
    exit 1
  fi
  java_version="$(sed -n 's/^JAVA_VERSION="\([^"]*\)"/\1/p' "${release_file}")"
  case "${java_version}" in
    17|17.*) ;;
    *)
      echo "Java 17 runtime is required, found: ${java_version:-unknown}" >&2
      exit 1
      ;;
  esac
}

write_manifest() {
  cat > "${WORK_DIR}/manifest.txt" <<EOF
app=${APP_NAME}
version=${PACKAGE_VERSION}
built_at=$(date -Iseconds)
backend_jar=backend/loadtest-platform.jar
frontend=embedded
runtime_dir=runtime/
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
  need_command tar
  mkdir -p "${OUTPUT_DIR}"
  if [[ -e "${WORK_DIR}" || -e "${PACKAGE_FILE}" || -e "${PACKAGE_FILE}.sha256" ]]; then
    echo "Package output already exists. Choose another PACKAGE_VERSION: ${PACKAGE_VERSION}" >&2
    exit 1
  fi

  local runtime_dir
  runtime_dir="$(resolve_runtime_dir)"
  validate_runtime "${runtime_dir}"

  echo "==> Project root: ${PROJECT_ROOT}"
  echo "==> Output dir: ${OUTPUT_DIR}"

  SKIP_TESTS="${SKIP_TESTS}" bash "${SCRIPT_DIR}/build-app.sh"

  echo "==> Assembling offline package"
  mkdir -p "${WORK_DIR}/backend" "${WORK_DIR}/runtime" "${WORK_DIR}/scripts"

  cp "$(latest_backend_jar)" "${WORK_DIR}/backend/loadtest-platform.jar"
  cp "${BACKEND_DIR}/scripts/start.sh" "${WORK_DIR}/backend/start.sh"
  cp "${BACKEND_DIR}/scripts/loadtest-platform.service" "${WORK_DIR}/backend/loadtest-platform.service"
  cp -a "${runtime_dir}/." "${WORK_DIR}/runtime/"
  cp "${SCRIPT_DIR}/deploy-offline-package.sh" "${WORK_DIR}/scripts/deploy-offline-package.sh"
  chmod +x "${WORK_DIR}/backend/start.sh" "${WORK_DIR}/runtime/bin/java" "${WORK_DIR}/scripts/deploy-offline-package.sh"
  write_manifest

  (cd "${OUTPUT_DIR}" && tar -czf "$(basename "${PACKAGE_FILE}")" "$(basename "${WORK_DIR}")")
  create_checksum

  echo "==> Offline package created:"
  echo "${PACKAGE_FILE}"
  if [[ -f "${PACKAGE_FILE}.sha256" ]]; then
    echo "${PACKAGE_FILE}.sha256"
  fi
}

main "$@"
