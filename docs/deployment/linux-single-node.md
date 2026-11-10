# Linux 单机部署指南

平台以前后端合一的 JAR 部署，默认由 systemd 管理，在 `8080` 端口提供页面和 API，无需安装 Nginx。数据库使用本机 SQLite 文件，首次启动时 Flyway 会创建表结构。

## 1. 环境要求

- 构建机：JDK 17、Maven 3.9+、Node.js 20+ 和 npm。
- 部署机：使用 systemd 的 Linux 主机。离线包自带 Java 17 运行时，运行时的架构须与部署机一致。

## 2. 打包与部署

离线部署请按 [README 的打包与部署步骤](../../README.md#打包) 构建并安装。也可在目标服务器的项目根目录从源码部署：

```bash
RUNTIME_SOURCE=/path/to/linux-java-17 bash backend/scripts/deploy-backend.sh
```

部署脚本会配置服务，默认将 JAR 和启动脚本放在 `/opt/loadtest-platform/backend/`，Java 放在 `runtime/`，数据库放在 `data/loadtest-platform.db`。

## 3. 服务状态和日志

```bash
sudo systemctl status loadtest-platform
journalctl -u loadtest-platform -f
```

## 4. JMeter 与数据源要求

- JMeter 服务器可以与平台在同一台机器，也可以是另一台 Linux 主机。
- 平台通过 SSH 登录 JMeter 服务器执行命令。
- JMeter 脚本需要把指标写入 InfluxDB，测试结果生成阶段会查询 InfluxDB。
- Prometheus 需要能查询到被压测服务器的 node_exporter 指标。
- 项目中的 Prometheus 数据源 `extraConfigJson` 建议填写：

```json
{"instances":["10.0.0.11:9100","10.0.0.12:9100"]}
```

## 5. SQLite 备份

停止服务后备份最稳妥：

```bash
sudo systemctl stop loadtest-platform
sudo cp /opt/loadtest-platform/data/loadtest-platform.db /opt/loadtest-platform/data/loadtest-platform.db.$(date +%Y%m%d%H%M%S).bak
sudo systemctl start loadtest-platform
```

也可以使用 SQLite 在线备份：

```bash
sqlite3 /opt/loadtest-platform/data/loadtest-platform.db ".backup '/opt/loadtest-platform/data/loadtest-platform.db.$(date +%Y%m%d%H%M%S).bak'"
```

## 6. 升级

构建新的离线包后，沿用原部署参数重新执行部署脚本。脚本默认备份现有 SQLite 数据库，并在更新应用后重启服务。

## 7. 验证

```bash
curl http://127.0.0.1:8080/api/projects
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/
```

浏览器访问服务器地址后，验证以下最小闭环：

```text
创建项目 -> 配置 JMeter 和数据源 -> 创建任务 -> 发起执行 -> 生成结果 -> 生成报告
```
