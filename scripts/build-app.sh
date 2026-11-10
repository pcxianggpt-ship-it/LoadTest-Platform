#!/usr/bin/env bash
set -euo pipefail

SKIP_TESTS="${SKIP_TESTS:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"

for command in mvn node npm jar; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Missing command: ${command}" >&2
    exit 1
  fi
done

echo "==> Building frontend with same-origin API"
cd "${FRONTEND_DIR}"
npm ci
unset VITE_API_BASE_URL
if [[ "${SKIP_TESTS}" != "true" ]]; then
  npm test
fi
npm run build

if [[ ! -f dist/index.html ]]; then
  echo "Frontend entry file not found: ${FRONTEND_DIR}/dist/index.html" >&2
  exit 1
fi
if grep -R -E "localhost:8080|/api/api|[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:8080" dist >/dev/null 2>&1; then
  echo "Invalid API URL found in frontend dist. Check VITE_API_BASE_URL and rebuild." >&2
  exit 1
fi

echo "==> Building application JAR with embedded frontend"
cd "${BACKEND_DIR}"
if [[ "${SKIP_TESTS}" == "true" ]]; then
  mvn clean package -Pfrontend -DskipTests
else
  mvn clean package -Pfrontend
fi

app_jar="$(find "${BACKEND_DIR}/target" -maxdepth 1 -type f -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | sort | tail -n 1)"
if [[ -z "${app_jar}" ]] || ! jar tf "${app_jar}" | grep -Fx 'BOOT-INF/classes/static/index.html' >/dev/null; then
  echo "Application JAR is missing the embedded frontend" >&2
  exit 1
fi
echo "==> Application JAR created: ${app_jar}"
