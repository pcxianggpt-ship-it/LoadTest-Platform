#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
用法：bash start-monitor.sh [--check]
--check 只检查当前运行时、容器及缺失的离线资源。

RESOURCE_DIR     资源目录，默认 /root/app/resource
RUNTIME          auto、docker 或 nerdctl，默认 auto
NERDCTL_NAMESPACE nerdctl 命名空间，默认沿用本机设置
DOCKER_REGISTRY_HOST 安装脚本所需的仓库主机名，默认 127.0.0.1
NODE_EXPORTER_TARGET 新机首次恢复配置时使用的采集地址，默认 本机IPv4:9100

镜像使用 docker save 的单架构 tar，放在 resource/images 下。
支持现有 *-amd.tar 和对应的 *-arm.tar，也支持 -amd64/-arm64 或架构子目录。
ARM64 Docker 包可放在 resource/docker/docker-29.9.3-arm64.tgz。
EOF
}

die() { printf '错误：%s\n' "$*" >&2; exit 1; }

check=false
case "$#:${1:-}" in
    0:) ;;
    1:--check) check=true ;;
    1:--help|1:-h) usage; exit ;;
    *) usage >&2; exit 2 ;;
esac

[[ $EUID -eq 0 ]] || die '请用 root 运行。'
case "$(uname -s)/$(uname -m)" in
    Linux/x86_64|Linux/amd64) arch=amd64; short_arch=amd; elf_machine=62 ;;
    Linux/aarch64|Linux/arm64) arch=arm64; short_arch=arm; elf_machine=183 ;;
    *) die '仅支持 Linux AMD64 和 ARM64。' ;;
esac
platform="linux/$arch"
resource="${RESOURCE_DIR:-/root/app/resource}"
resource="$(cd "$resource" && pwd -P)" || die "资源目录不存在：$resource"
images="$resource/images"
data="$resource/stress-test"
backup="$resource/volum-monitor.tar"
network=stress-test-net
runtime="${RUNTIME:-auto}"
case "$runtime" in auto|docker|nerdctl) ;; *) die "未知运行时：$runtime" ;; esac

services=(influxdb prometheus grafana)
volumes=(influxdb-data prometheus-conf prometheus-data grafana-data)
declare -A image=(
    [influxdb]=influxdb:1.8.10
    [prometheus]=bitnami/prometheus:latest
    [grafana]=grafana/grafana-enterprise:13.3.1
)
declare -A archive_name=(
    [influxdb]=influxdb-1.8 [prometheus]=prometheus-yc [grafana]=grafana13.3.1
)
declare -A present=() archives=()
missing_volumes=()
docker_cli=(docker --host unix:///var/run/docker.sock)
nerdctl_cli=(nerdctl)
[[ -z ${NERDCTL_NAMESPACE:-} ]] || nerdctl_cli+=(--namespace "$NERDCTL_NAMESPACE")
install_required=false
work=''

cleanup() {
    if [[ -n $work ]]; then
        rm -rf -- "$work"
    fi
}

has_name() { grep -Fxq -- "$2" <<< "$1"; }

has_monitor() {
    local service
    for service in "${services[@]}"; do
        if has_name "$1" "$service"; then return 0; fi
    done
    return 1
}

service_installed() {
    command -v systemctl >/dev/null 2>&1 && systemctl cat "$1.service" >/dev/null 2>&1
}

select_runtime() {
    local docker_names='' nerdctl_names='' docker_ready=false nerdctl_ready=false
    if [[ $runtime != nerdctl ]] && command -v docker >/dev/null; then
        if docker_names=$("${docker_cli[@]}" ps -a --format '{{.Names}}' 2>/dev/null); then
            docker_ready=true
        elif ! $check && service_installed docker; then
            if systemctl start docker && docker_names=$("${docker_cli[@]}" ps -a --format '{{.Names}}'); then
                docker_ready=true
            fi
        fi
    fi
    if [[ $runtime != docker ]] && command -v nerdctl >/dev/null; then
        if nerdctl_names=$("${nerdctl_cli[@]}" ps -a --format '{{.Names}}' 2>/dev/null); then
            nerdctl_ready=true
        elif ! $check && ! $docker_ready && service_installed containerd; then
            if systemctl start containerd && nerdctl_names=$("${nerdctl_cli[@]}" ps -a --format '{{.Names}}'); then
                nerdctl_ready=true
            fi
        fi
    fi

    if $docker_ready && $nerdctl_ready && has_monitor "$docker_names" && has_monitor "$nerdctl_names"; then
        die '两种运行时都有监控容器，请用 RUNTIME=docker 或 RUNTIME=nerdctl 指定。'
    elif $nerdctl_ready && { ! $docker_ready || has_monitor "$nerdctl_names"; }; then
        engine=("${nerdctl_cli[@]}")
        names="$nerdctl_names"
    elif $docker_ready; then
        engine=("${docker_cli[@]}")
        names="$docker_names"
    elif [[ $runtime == nerdctl ]] || command -v dockerd >/dev/null ||
         command -v containerd >/dev/null || service_installed docker || service_installed containerd ||
         [[ -S /var/run/docker.sock || -S /run/containerd/containerd.sock ]]; then
        die '发现已安装的容器运行时，但无法连接。请检查服务及 nerdctl 命名空间。'
    else
        engine=("${docker_cli[@]}")
        names=''
        install_required=true
    fi
}

archive_member() {
    tar --extract --to-stdout --occurrence=1 --file="$1" -- "$2"
}

find_archive() {
    local stem="${archive_name[$1]}" file
    for file in "$images/$arch/$stem.tar" "$images/$stem-$arch.tar" "$images/$stem-$short_arch.tar"; do
        if [[ -f $file ]]; then printf '%s\n' "$file"; return; fi
    done
    die "$1 缺少 $platform 镜像。请提供 $images/$stem-$arch.tar。"
}

check_archive() {
    local file="$1" expected="$2" manifest config metadata tags
    local -a configs
    manifest=$(archive_member "$file" manifest.json) || die "无法读取 Docker 镜像归档：$file"
    manifest=$(tr -d '\r\n' <<< "$manifest")
    mapfile -t configs < <(grep -oE '"Config"[[:space:]]*:[[:space:]]*"[^"]+"' <<< "$manifest")
    [[ ${#configs[@]} -eq 1 ]] || die "需要只包含一个架构镜像的 Docker 归档：$file"
    config=$(sed -E 's/^[^:]+:[[:space:]]*"([^"]+)"/\1/' <<< "${configs[0]}")
    [[ $config =~ ^([a-f0-9]{64}\.json|blobs/sha256/[a-f0-9]{64})$ ]] || die "镜像配置路径无效：$file"
    tags=$(grep -oE '"RepoTags"[[:space:]]*:[[:space:]]*\[[^]]*\]' <<< "$manifest") ||
        die "镜像归档没有标签：$file"
    if [[ $tags != *"\"$expected\""* && $tags != *"\"docker.io/$expected\""* &&
          $tags != *"\"docker.io/library/$expected\""* ]]; then
        die "$file 未包含标签 $expected。"
    fi
    metadata=$(archive_member "$file" "$config") || die "无法读取镜像配置：$file"
    grep -Eq "\"architecture\"[[:space:]]*:[[:space:]]*\"$arch\"" <<< "$metadata" &&
        grep -Eq '"os"[[:space:]]*:[[:space:]]*"linux"' <<< "$metadata" ||
        die "$file 的镜像平台不是 $platform。"
    if [[ $expected == bitnami/prometheus:latest ]]; then
        grep -Eq '"org\.opencontainers\.image\.version"[[:space:]]*:[[:space:]]*"2\.47\.1"' <<< "$metadata" ||
            die "$file 需要与现场一致的 Bitnami Prometheus 2.47.1。"
    fi
}

normalize_image() {
    local ref="${1#docker.io/}"
    printf '%s' "${ref#library/}"
}

inspect_image() {
    "${engine[@]}" image inspect --format '{{.Os}}/{{.Architecture}}' "$1"
}

check_local_image() {
    local actual="$1" expected="$2" version
    [[ $actual == "$platform" ]] || die "$expected 的平台为 $actual，预期 $platform。"
    if [[ $expected == bitnami/prometheus:latest ]]; then
        version=$("${engine[@]}" image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$expected")
        [[ $version == 2.47.1 ]] || die "$expected 需要版本 2.47.1，当前为 $version。"
    fi
}

plan_services() {
    local service ref actual file
    for service in "${services[@]}"; do
        ref="${image[$service]}"
        if has_name "$names" "$service"; then
            actual=$("${engine[@]}" container inspect --format '{{.Config.Image}}' "$service")
            [[ $(normalize_image "$actual") == "$ref" ]] || die "同名容器 $service 正在使用 $actual，预期 $ref。"
            present[$service]=true
            printf '已有容器：%s (%s)\n' "$service" "$ref"
        elif ! $install_required && actual=$(inspect_image "$ref" 2>/dev/null); then
            check_local_image "$actual" "$ref"
            printf '已有镜像：%s (%s)\n' "$ref" "$platform"
        else
            file=$(find_archive "$service")
            check_archive "$file" "$ref"
            archives[$service]="$file"
            printf '待导入：%s\n' "$file"
        fi
    done
}

plan_volumes() {
    local name listing
    [[ ! -L $data && ( ! -e $data || -d $data ) ]] || die "挂载根目录无效：$data"
    for name in "${volumes[@]}"; do
        [[ ! -L $data/$name && ( ! -e $data/$name || -d $data/$name ) ]] ||
            die "挂载目录无效：$data/$name"
        [[ -d $data/$name ]] || missing_volumes+=("stress-test/$name")
    done
    if [[ -d $data/prometheus-conf ]]; then
        [[ -f $data/prometheus-conf/prometheus.yml ]] ||
            die "现有配置目录缺少 prometheus.yml：$data/prometheus-conf"
    fi
    if (( ${#missing_volumes[@]} )); then
        [[ -f $backup ]] || die "找不到挂载备份：$backup"
        listing=$(tar -tf "$backup") || die "无法读取挂载备份：$backup"
        if grep -Eq '^/|(^|/)\.\.(/|$)' <<< "$listing"; then die '备份包含不安全的路径。'; fi
        tar -tvf "$backup" | awk 'substr($0,1,1) !~ /^[-d]$/ { bad=1 } END { exit bad }' ||
            die '挂载备份应只包含目录和普通文件。'
        for name in "${missing_volumes[@]}"; do
            has_name "$listing" "$name/" || die "备份缺少 $name。"
            printf '待恢复：%s\n' "$resource/$name"
        done
    fi
}

check_installer() {
    local dir="$resource/docker" candidate header
    local -a bytes
    installer="$dir/docker_install.sh"
    [[ -f $installer ]] || die "找不到安装脚本：$installer"
    command -v systemctl >/dev/null || die '离线 Docker 安装脚本需要 systemd。'
    for candidate in "$dir/$arch/docker-29.9.3.tgz" "$dir/docker-29.9.3-$arch.tgz" "$dir/docker-29.9.3.tgz"; do
        [[ -f $candidate ]] || continue
        header=$(archive_member "$candidate" docker/dockerd | { od -An -tu1 -N20; cat >/dev/null; }) ||
            die "无法读取 Docker 安装包：$candidate"
        read -ra bytes <<< "$(tr '\n' ' ' <<< "$header")"
        if [[ "${bytes[*]:0:6}" == '127 69 76 70 2 1' && ${bytes[18]:-} == "$elf_machine" && ${bytes[19]:-} == 0 ]]; then
            bundle="$candidate"
            printf '待安装：%s (%s)\n' "$bundle" "$platform"
            return
        fi
    done
    die "缺少 $platform 的 Docker 29.9.3 安装包。请放到 $dir/docker-29.9.3-$arch.tgz。"
}

install_docker() {
    local registry="${DOCKER_REGISTRY_HOST:-127.0.0.1}" status=0
    [[ $registry =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] || die 'DOCKER_REGISTRY_HOST 应为主机名或 IPv4 地址。'
    mkdir "$work/docker-install"
    ln -s -- "$bundle" "$work/docker-install/docker-29.9.3.tgz"
    (cd "$work/docker-install" && bash "$installer" "$registry") || status=$?
    if [[ -S /var/run/docker.sock ]]; then chmod o-rw /var/run/docker.sock; fi
    (( status == 0 )) || die "Docker 安装脚本退出码：$status"
    "${engine[@]}" ps >/dev/null || die 'Docker 安装后仍无法连接。'
}

write_prometheus_config() {
    local file="$1" address="${NODE_EXPORTER_TARGET:-}" ip_addr
    if [[ -z $address ]]; then
        ip_addr=$(ip -4 route get 192.0.2.1 2>/dev/null |
            awk '{for (i=1;i<NF;i++) if ($i=="src") {print $(i+1); exit}}' || true)
        if [[ -z $ip_addr ]]; then
            ip_addr=$(ip -o -4 addr show scope global |
                awk '$2 !~ /^(docker|br-|cni|veth)/ {sub(/\/.*/, "", $4); print $4; exit}')
        fi
        [[ -n $ip_addr ]] || die '无法判断本机 IPv4，请设置 NODE_EXPORTER_TARGET=地址:端口。'
        address="$ip_addr:9100"
    fi
    [[ $address =~ ^[A-Za-z0-9][A-Za-z0-9.-]*:[0-9]{1,5}$ ]] ||
        die 'NODE_EXPORTER_TARGET 应为 主机名或IPv4:端口。'
    cat > "$file" <<EOF
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
  - job_name: 'node-exporter'
    static_configs:
      - targets: ['$address']
EOF
    printf 'Node Exporter 采集地址：%s\n' "$address"
}

restore_volumes() {
    local name
    (( ${#missing_volumes[@]} )) || return 0
    mkdir "$work/volumes"
    tar --extract --preserve-permissions --numeric-owner --file="$backup" \
        --directory="$work/volumes" -- "${missing_volumes[@]}"
    if [[ -d $work/volumes/stress-test/prometheus-conf ]]; then
        write_prometheus_config "$work/volumes/stress-test/prometheus-conf/prometheus.yml"
    fi
    mkdir -p -- "$data"
    for name in "${missing_volumes[@]}"; do
        [[ ! -e $resource/$name && ! -L $resource/$name ]] || die "恢复时发现目录已存在：$resource/$name"
        mv -T --no-clobber -- "$work/volumes/$name" "$resource/$name"
        [[ ! -e $work/volumes/$name && -d $resource/$name ]] || die "未能恢复 $name。"
    done
}

start_service() {
    local service="$1" port memory swap
    local -a mounts
    if [[ ${present[$service]:-} == true ]]; then
        if [[ $("${engine[@]}" container inspect --format '{{.State.Running}}' "$service") == true ]]; then
            printf '已运行：%s\n' "$service"
        else
            "${engine[@]}" start "$service"
        fi
        return
    fi
    case "$service" in
        influxdb)
            port=8086; memory=2g; swap=4g
            mounts=(-v "$data/influxdb-data:/var/lib/influxdb") ;;
        prometheus)
            port=9090; memory=1g; swap=2g
            mounts=(-v "$data/prometheus-conf:/opt/bitnami/prometheus/conf"
                    -v "$data/prometheus-data:/opt/bitnami/prometheus/data") ;;
        grafana)
            port=3000; memory=1g; swap=2g
            mounts=(-v "$data/grafana-data:/var/lib/grafana") ;;
    esac
    "${engine[@]}" run -d --pull=never --platform "$platform" --name "$service" \
        --network "$network" --restart=always --cpus=1 --memory "$memory" --memory-swap "$swap" \
        --log-driver=json-file --log-opt max-size=500m --log-opt max-file=3 \
        -p "$port:$port" -v /etc/localtime:/etc/localtime "${mounts[@]}" "${image[$service]}"
}

if ! $check; then
    command -v flock >/dev/null || die '需要 flock 防止并发部署。'
    exec 9>"$resource/.monitor-start.lock"
    flock -n 9 || die '已有监控启动任务正在执行。'
fi

select_runtime
printf '平台：%s，运行时：%s\n' "$platform" "${engine[0]}"
if $install_required; then check_installer; fi
plan_services
plan_volumes
if $check; then
    printf '检查通过。\n'
    exit
fi

if $install_required || (( ${#missing_volumes[@]} )); then
    work=$(mktemp -d "$resource/.monitor-start.XXXXXX")
    trap cleanup EXIT
fi
if $install_required; then install_docker; fi
for service in "${services[@]}"; do
    if [[ -n ${archives[$service]:-} ]]; then
        "${engine[@]}" load -i "${archives[$service]}"
        check_local_image "$(inspect_image "${image[$service]}")" "${image[$service]}"
    fi
done
restore_volumes
if ! "${engine[@]}" network inspect "$network" >/dev/null 2>&1; then
    "${engine[@]}" network create --driver bridge "$network"
fi
for service in "${services[@]}"; do start_service "$service"; done
for service in "${services[@]}"; do
    [[ $("${engine[@]}" container inspect --format '{{.State.Running}}' "$service") == true ]] ||
        die "$service 未运行，请用 ${engine[0]} logs $service 检查。"
done
printf '监控容器已启动。InfluxDB 8086，Prometheus 9090，Grafana 3000。\n'
