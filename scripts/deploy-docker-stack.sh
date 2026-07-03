#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${RUNTIME:-nerdctl}"
BASE_DIR="${BASE_DIR:-/opt/stress-test}"
NETWORK_NAME="${NETWORK_NAME:-stress-test-net}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IMAGE_DIR="${IMAGE_DIR:-${PROJECT_ROOT}/images}"
LOAD_IMAGES="${LOAD_IMAGES:-true}"

INFLUXDB_IMAGE="${INFLUXDB_IMAGE:-10.33.1.9:5000/performance-testing/influxdb:1.8.10}"
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-10.33.1.9:5000/performance-testing/prometheus:latest}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-10.33.1.9:5000/performance-testing/grafana-enterprise:2.0}"
GRAFANA_RENDERER_IMAGE="${GRAFANA_RENDERER_IMAGE:-grafana/grafana-image-renderer:v5.3.0}"
JMETER_IMAGE="${JMETER_IMAGE:-10.33.1.9:5000/stress-test/jmeter:5.2.1}"

INFLUXDB_PORT="${INFLUXDB_PORT:-8086}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
GRAFANA_PORT="${GRAFANA_PORT:-3000}"
GRAFANA_RENDERER_PORT="${GRAFANA_RENDERER_PORT:-8081}"
JMETER_HOST_PORT="${JMETER_HOST_PORT:-8090}"
JMETER_CONTAINER_PORT="${JMETER_CONTAINER_PORT:-8080}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [deploy|start|stop|restart|status|logs|rm]

Commands:
  deploy   Create directories, network, and recreate all containers (default)
  start    Start existing containers
  stop     Stop containers
  restart  Stop then start existing containers
  status   Show containers in ${NETWORK_NAME}
  logs     Follow logs for a container, set SERVICE=name to choose one
  rm       Remove containers and network

Environment overrides:
  RUNTIME=${RUNTIME}
  BASE_DIR=${BASE_DIR}
  NETWORK_NAME=${NETWORK_NAME}
  IMAGE_DIR=${IMAGE_DIR}
  LOAD_IMAGES=${LOAD_IMAGES}
  INFLUXDB_IMAGE=${INFLUXDB_IMAGE}
  PROMETHEUS_IMAGE=${PROMETHEUS_IMAGE}
  GRAFANA_IMAGE=${GRAFANA_IMAGE}
  GRAFANA_RENDERER_IMAGE=${GRAFANA_RENDERER_IMAGE}
  JMETER_IMAGE=${JMETER_IMAGE}
EOF
}

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

container_exists() {
  "${RUNTIME}" ps -a --format '{{.Names}}' | grep -Fxq "$1"
}

network_exists() {
  "${RUNTIME}" network ls --format '{{.Name}}' | grep -Fxq "${NETWORK_NAME}"
}

load_images() {
  if [[ "${LOAD_IMAGES}" != "true" ]]; then
    echo "Image load skipped because LOAD_IMAGES=${LOAD_IMAGES}"
    return
  fi
  if [[ ! -d "${IMAGE_DIR}" ]]; then
    echo "Image directory not found: ${IMAGE_DIR}" >&2
    exit 1
  fi

  local found=false
  local image_file
  while IFS= read -r -d '' image_file; do
    found=true
    echo "Loading image archive: ${image_file}"
    "${RUNTIME}" load -i "${image_file}"
  done < <(find "${IMAGE_DIR}" -maxdepth 1 -type f \( \
      -name "*.tar" -o \
      -name "*.tar.gz" -o \
      -name "*.tgz" -o \
      -name "*.tar.xz" -o \
      -name "*.txz" -o \
      -name "*.tar.zst" \
    \) -print0 | sort -z)

  if [[ "${found}" != "true" ]]; then
    echo "No image archives found in ${IMAGE_DIR}" >&2
    exit 1
  fi
}

ensure_directories() {
  run_as_root mkdir -p \
    "${BASE_DIR}/influxdb-data" \
    "${BASE_DIR}/prometheus-data" \
    "${BASE_DIR}/prometheus-conf" \
    "${BASE_DIR}/grafana-data" \
    "${BASE_DIR}/jmeter-script"

  run_as_root chown -R 1001:1001 "${BASE_DIR}/prometheus-data" "${BASE_DIR}/prometheus-conf"
  run_as_root chown -R 472:472 "${BASE_DIR}/grafana-data"
  run_as_root chmod -R 755 "${BASE_DIR}/grafana-data"

  run_as_root rm -f "${BASE_DIR}/prometheus-data/lock"
}

ensure_network() {
  if network_exists; then
    return
  fi
  "${RUNTIME}" network create "${NETWORK_NAME}"
}

remove_container_if_exists() {
  local name="$1"
  if container_exists "${name}"; then
    "${RUNTIME}" rm -f "${name}"
  fi
}

deploy_influxdb() {
  remove_container_if_exists influxdb
  "${RUNTIME}" run -d \
    --name influxdb \
    --network "${NETWORK_NAME}" \
    --restart=always \
    -p "${INFLUXDB_PORT}:8086" \
    -v "${BASE_DIR}/influxdb-data:/var/lib/influxdb" \
    -v /etc/localtime:/etc/localtime:ro \
    --memory=2g \
    --cpus=1 \
    "${INFLUXDB_IMAGE}"
}

deploy_prometheus() {
  remove_container_if_exists prometheus
  "${RUNTIME}" run -d \
    --name prometheus \
    --network "${NETWORK_NAME}" \
    --restart=always \
    -p "${PROMETHEUS_PORT}:9090" \
    -v "${BASE_DIR}/prometheus-conf:/opt/bitnami/prometheus/conf" \
    -v "${BASE_DIR}/prometheus-data:/opt/bitnami/prometheus/data" \
    -v /etc/localtime:/etc/localtime:ro \
    --memory=1g \
    --cpus=1 \
    "${PROMETHEUS_IMAGE}"
}

deploy_grafana() {
  remove_container_if_exists grafana
  "${RUNTIME}" run -d \
    --name grafana \
    --network "${NETWORK_NAME}" \
    --restart=always \
    -p "${GRAFANA_PORT}:3000" \
    -v "${BASE_DIR}/grafana-data:/var/lib/grafana" \
    -v /etc/localtime:/etc/localtime:ro \
    --memory=1g \
    --cpus=1 \
    "${GRAFANA_IMAGE}"
}

deploy_grafana_renderer() {
  remove_container_if_exists grafana-renderer
  "${RUNTIME}" run -d \
    --name grafana-renderer \
    --network "${NETWORK_NAME}" \
    --restart=always \
    -p "${GRAFANA_RENDERER_PORT}:8081" \
    --shm-size=1g \
    -e API_DEFAULT_ENCODING=png \
    "${GRAFANA_RENDERER_IMAGE}"
}

deploy_jmeter() {
  remove_container_if_exists jmeter
  "${RUNTIME}" run -d \
    --name jmeter \
    --network "${NETWORK_NAME}" \
    --restart=always \
    -p "${JMETER_HOST_PORT}:${JMETER_CONTAINER_PORT}" \
    -v "${BASE_DIR}/jmeter-script:/scrpit" \
    -v /etc/localtime:/etc/localtime:ro \
    --memory=2g \
    --cpus=1 \
    "${JMETER_IMAGE}"
}

deploy_all() {
  need_command "${RUNTIME}"
  need_command find
  ensure_directories
  load_images
  ensure_network
  deploy_influxdb
  deploy_prometheus
  deploy_grafana
  deploy_grafana_renderer
  deploy_jmeter
  status
  print_access_info
}

start_all() {
  need_command "${RUNTIME}"
  "${RUNTIME}" start influxdb prometheus grafana grafana-renderer jmeter
}

stop_all() {
  need_command "${RUNTIME}"
  "${RUNTIME}" stop jmeter grafana-renderer grafana prometheus influxdb
}

restart_all() {
  stop_all
  start_all
}

remove_all() {
  need_command "${RUNTIME}"
  for name in jmeter grafana-renderer grafana prometheus influxdb; do
    remove_container_if_exists "${name}"
  done
  if network_exists; then
    "${RUNTIME}" network rm "${NETWORK_NAME}"
  fi
}

status() {
  need_command "${RUNTIME}"
  "${RUNTIME}" ps --filter "network=${NETWORK_NAME}"
}

show_logs() {
  need_command "${RUNTIME}"
  local service="${SERVICE:-grafana}"
  "${RUNTIME}" logs -f "${service}"
}

print_access_info() {
  cat <<EOF

Deployment finished.
Grafana:       http://<server-ip>:${GRAFANA_PORT}
Prometheus:    http://<server-ip>:${PROMETHEUS_PORT}
InfluxDB:      http://<server-ip>:${INFLUXDB_PORT}
JMeter:        http://<server-ip>:${JMETER_HOST_PORT}
Renderer:      http://<server-ip>:${GRAFANA_RENDERER_PORT}

Internal service URLs:
  InfluxDB:    http://influxdb:8086
  Prometheus:  http://prometheus:9090
EOF
}

main() {
  local command="${1:-deploy}"
  case "${command}" in
    deploy) deploy_all ;;
    start) start_all ;;
    stop) stop_all ;;
    restart) restart_all ;;
    status) status ;;
    logs) show_logs ;;
    rm) remove_all ;;
    -h|--help|help) usage ;;
    *)
      usage >&2
      exit 1
      ;;
  esac
}

main "$@"
