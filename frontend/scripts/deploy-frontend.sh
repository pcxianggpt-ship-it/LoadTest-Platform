#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo "Frontend is embedded in the application JAR; building and deploying the application."
exec bash "${PROJECT_ROOT}/backend/scripts/deploy-backend.sh" "$@"
