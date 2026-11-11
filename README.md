# LoadTest-Platform

## 启动与访问

平台由后端直接提供前端页面和 `/api` 接口，无需安装 Nginx。离线部署默认访问地址为 `http://<服务器IP>:2800/`，直接运行 JAR 时默认端口为 8080。页面刷新和详情页直接访问均由后端处理。

前端构建文件嵌入后端 JAR，页面、静态资源和接口随同一个应用包发布，无需单独部署前端目录。

## 打包

在 Linux 环境或 WSL 中，从项目根目录构建前后端合一的 JAR：

```bash
bash scripts/build-app.sh
```

构建脚本依次安装前端依赖、运行测试、构建前端，再将前端文件打入后端 JAR。产物位于 `backend/target/`。直接运行该 JAR 即可同时访问页面和接口。

在 Linux 或 WSL 中构建包含 Java 17 运行时的离线部署包：

```bash
./scripts/build-offline-package.sh
```

脚本默认使用 `JAVA_HOME` 指向的 Java 17，否则从 `PATH` 查找 `java`。也可通过 `RUNTIME_SOURCE=/path/to/linux-java-17` 或 `RUNTIME_ARCHIVE=/path/to/linux-java-17.tar.gz` 显式指定运行时。运行时架构必须与目标服务器一致。`SKIP_TESTS=true` 可跳过前后端测试，但仍执行前端构建和 JAR 打包。

离线包包含 `backend/`（应用 JAR、启动脚本和服务模板）、`runtime/`、`scripts/` 和 `manifest.txt`。默认输出到 `offline-dist/`，以打包时间为版本号；可用 `PACKAGE_VERSION=v0.2.0` 指定版本号。

## 部署

将离线包传到目标服务器，解压后执行包内的部署脚本（示例版本为 `v0.2.0`）：

```bash
tar -xzf loadtest-platform-offline-v0.2.0.tar.gz
sudo bash loadtest-platform-v0.2.0/scripts/deploy-offline-package.sh /path/to/loadtest-platform-offline-v0.2.0.tar.gz
```

离线部署默认使用 TCP 2800，部署前会检查端口是否已被其他进程占用。应用服务启动后，脚本会自动关闭 firewalld 和 UFW（如存在），并打印服务器的实际访问地址。

使用 `SERVER_PORT` 自定义前端和接口共同使用的端口：

```bash
sudo SERVER_PORT=9090 bash loadtest-platform-v0.2.0/scripts/deploy-offline-package.sh /path/to/loadtest-platform-offline-v0.2.0.tar.gz
```

从源码部署时，`backend/scripts/deploy-backend.sh` 会统一构建和部署前后端。原有 `frontend/scripts/deploy-frontend.sh` 入口也转为调用这一流程，前端修改需要重新构建并重启整个应用。

本地开发继续使用 Vite 的 `/api` 代理；后端可单独运行 `mvn spring-boot:run`。验证构建后的整包时，从项目根目录构建后运行：

```bash
java -jar backend/target/loadtest-platform-backend-0.1.0-SNAPSHOT.jar
```

新增前端页面路由时，同步更新 `FrontendWebConfig` 中的页面路由映射。
