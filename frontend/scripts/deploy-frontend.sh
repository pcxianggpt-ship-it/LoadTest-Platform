#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/loadtest-platform}"
FRONTEND_TARGET="${FRONTEND_TARGET:-$APP_HOME/frontend}"
APP_USER="${APP_USER:-loadtest}"
APP_GROUP="${APP_GROUP:-loadtest}"
RELOAD_NGINX="${RELOAD_NGINX:-true}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

run_sudo() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

echo "==> Checking frontend deployment tools"
need_command node
need_command npm
need_command rsync

echo "==> Frontend source: $FRONTEND_DIR"
echo "==> Frontend target: $FRONTEND_TARGET"

cd "$FRONTEND_DIR"

echo "==> Installing dependencies"
if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi

echo "==> Building frontend with same-origin API"
rm -rf dist
unset VITE_API_BASE_URL
npm run build

echo "==> Checking generated API URLs"
if grep -R -E "localhost:8080|/api/api|[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:8080" dist >/dev/null 2>&1; then
  echo "Invalid API URL found in frontend dist. Check VITE_API_BASE_URL and rebuild." >&2
  exit 1
fi

echo "==> Publishing frontend files"
run_sudo mkdir -p "$FRONTEND_TARGET"
run_sudo rsync -av --delete dist/ "$FRONTEND_TARGET/"

if id "$APP_USER" >/dev/null 2>&1 && getent group "$APP_GROUP" >/dev/null 2>&1; then
  run_sudo chown -R "$APP_USER:$APP_GROUP" "$FRONTEND_TARGET"
fi

if [ "$RELOAD_NGINX" = "true" ]; then
  echo "==> Validating and reloading nginx"
  run_sudo nginx -t
  run_sudo systemctl reload nginx
fi

echo "==> Frontend deployment finished"
