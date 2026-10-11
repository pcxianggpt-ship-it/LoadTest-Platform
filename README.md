# LoadTest-Platform

## 启动与访问

平台由后端直接提供前端页面和 `/api` 接口，无需安装 Nginx。默认访问地址为 `http://<服务器IP>:8080/`，页面刷新和详情页直接访问均由后端处理。

离线包部署后，启动脚本自动读取 `${APP_HOME}/frontend`，由后端提供页面和接口：

```bash
sudo bash scripts/deploy-offline-package.sh /path/to/loadtest-platform-offline-*.tar.gz
```

使用 `SERVER_PORT` 自定义前端和接口共同使用的端口：

```bash
sudo SERVER_PORT=9090 bash scripts/deploy-offline-package.sh /path/to/loadtest-platform-offline-*.tar.gz
```

本地开发继续使用 Vite 的 `/api` 代理。验证构建后的前端时，先在 `frontend` 目录运行 `npm run build`，再在 `backend` 目录启动后端：

```bash
LOADTEST_FRONTEND_DIR=../frontend/dist mvn spring-boot:run
```

直接运行 JAR 时，也可通过 `LOADTEST_FRONTEND_DIR` 指定前端文件目录。新增前端页面路由时，同步更新 `FrontendWebConfig` 中的页面路由映射。
