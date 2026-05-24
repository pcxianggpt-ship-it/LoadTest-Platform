# Linux 单机部署指南

本文档用于把压力测试平台 MVP 部署到一台 Linux 主机上。推荐目录为 `/opt/loadtest-platform`，数据库使用本机 SQLite 文件，后端由 systemd 管理，前端由 nginx 或其他静态文件服务托管。

## 1. 环境要求

- JDK 17：后端 Spring Boot 运行环境。
- Maven 3.9+：用于在服务器上构建后端；也可以在开发机打好 jar 后上传。
- Node.js 20+：用于构建前端；也可以在开发机执行 `npm run build` 后上传 `frontend/dist`。
- SQLite：不要求单独启动服务，运行时由 JDBC 访问数据库文件。
- nginx：用于托管前端静态文件并反向代理后端 API。

检查命令：

```bash
java -version
mvn -version
node -v
npm -v
sqlite3 --version
nginx -v
```

## 2. 目录规划

```text
/opt/loadtest-platform/
  backend/
    loadtest-platform.jar
    start.sh
  frontend/
    index.html
    assets/
  data/
    loadtest-platform.db
  logs/
```

创建目录：

```bash
sudo mkdir -p /opt/loadtest-platform/backend
sudo mkdir -p /opt/loadtest-platform/frontend
sudo mkdir -p /opt/loadtest-platform/data
sudo mkdir -p /opt/loadtest-platform/logs
sudo chown -R loadtest:loadtest /opt/loadtest-platform
```

如果没有专用用户，可以创建一个：

```bash
sudo useradd --system --home /opt/loadtest-platform --shell /sbin/nologin loadtest
```

## 3. 构建后端

在项目根目录执行：

```bash
cd backend
mvn clean package
```

构建完成后复制 jar：

```bash
sudo cp target/loadtest-platform-backend-0.1.0-SNAPSHOT.jar /opt/loadtest-platform/backend/loadtest-platform.jar
sudo cp scripts/start.sh /opt/loadtest-platform/backend/start.sh
sudo chmod +x /opt/loadtest-platform/backend/start.sh
sudo chown -R loadtest:loadtest /opt/loadtest-platform/backend
```

后端数据库路径使用：

```text
jdbc:sqlite:/opt/loadtest-platform/data/loadtest-platform.db
```

首次启动时 Flyway 会自动创建表结构。

## 4. 构建前端

在项目根目录执行：

```bash
bash frontend/scripts/deploy-frontend.sh
```

前端默认使用同源 API，也就是浏览器请求 `/api/...`，由 nginx 反向代理到后端。因此单机部署时不要把 `VITE_API_BASE_URL` 设置为 `http://服务器IP:8080` 或 `/api`，否则会引入跨域或 `/api/api/...` 的重复路径问题。

脚本会自动执行依赖安装、前端构建、构建产物检查、同步到 `/opt/loadtest-platform/frontend`，并重新加载 nginx。

如果需要自定义部署目录或用户：

```bash
APP_HOME=/opt/loadtest-platform APP_USER=loadtest APP_GROUP=loadtest bash frontend/scripts/deploy-frontend.sh
```

如果只想构建和同步，不重新加载 nginx：

```bash
RELOAD_NGINX=false bash frontend/scripts/deploy-frontend.sh
```

如果确实不使用 nginx 反向代理，而是让浏览器直接访问后端地址，构建前才需要设置完整后端地址：

```bash
export VITE_API_BASE_URL=http://your-server:8080
npm run build
```

这种方式需要后端额外配置 CORS。MVP 推荐使用 nginx 反向代理方式。

## 5. systemd 启动后端

复制服务模板：

```bash
sudo cp backend/scripts/loadtest-platform.service /etc/systemd/system/loadtest-platform.service
sudo systemctl daemon-reload
sudo systemctl enable loadtest-platform
sudo systemctl start loadtest-platform
```

查看状态和日志：

```bash
sudo systemctl status loadtest-platform
journalctl -u loadtest-platform -f
```

## 6. nginx 托管前端

示例配置：

```nginx
server {
    listen 80;
    server_name _;

    root /opt/loadtest-platform/frontend;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

启用配置后执行：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 7. JMeter 与数据源要求

- JMeter 服务器可以与平台在同一台机器，也可以是另一台 Linux 主机。
- 平台通过 SSH 登录 JMeter 服务器执行命令。
- JMeter 脚本需要把指标写入 InfluxDB，测试结果生成阶段会查询 InfluxDB。
- Prometheus 需要能查询到被压测服务器的 node_exporter 指标。
- 项目中的 Prometheus 数据源 `extraConfigJson` 建议填写：

```json
{"instances":["10.0.0.11:9100","10.0.0.12:9100"]}
```

## 8. SQLite 备份

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

## 9. 升级流程

```bash
sudo systemctl stop loadtest-platform
sudo cp /opt/loadtest-platform/data/loadtest-platform.db /opt/loadtest-platform/data/loadtest-platform.db.$(date +%Y%m%d%H%M%S).bak
sudo cp backend/target/loadtest-platform-backend-0.1.0-SNAPSHOT.jar /opt/loadtest-platform/backend/loadtest-platform.jar
sudo rsync -av --delete frontend/dist/ /opt/loadtest-platform/frontend/
sudo chown -R loadtest:loadtest /opt/loadtest-platform
sudo systemctl start loadtest-platform
```

## 10. 验证

```bash
curl http://127.0.0.1:8080/api/projects
curl http://127.0.0.1/
```

浏览器访问服务器地址后，验证以下最小闭环：

```text
创建项目 -> 配置 JMeter 和数据源 -> 创建任务 -> 发起执行 -> 生成结果 -> 生成报告
```
