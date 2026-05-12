# 压力测试平台 MVP 实施计划

> **给执行代理的要求：** 实施本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`。每个步骤使用复选框跟踪进度。

**目标：** 构建一个单人使用、单机部署的压力测试平台 MVP，支持创建项目、配置 JMeter 压测任务、通过 SSH 执行任务、归档指标，并生成文字版测试报告。

**架构：** 后端使用 Spring Boot，数据库使用本地 SQLite 文件，MyBatis-Plus 负责数据访问，Flyway 负责数据库结构迁移，Spring Scheduling 负责定时扫描和执行调度。前端使用 Vue 3 + Element Plus，通过 REST API 串起核心流程：项目 -> 任务 -> 执行 -> 结果 -> 报告。

**技术栈：** JDK 17、Spring Boot 3.x、MyBatis-Plus、SQLite JDBC、Flyway、Maven、Apache MINA SSHD、WebClient、Thymeleaf 或 Markdown 渲染、Vue 3、TypeScript、Vite、Element Plus、JUnit 5、Mockito。

---

## 技术栈决策

- 后端改用 Spring Boot，因为开发者更熟悉 Java，且部署环境可与 JMeter 共用 JDK。
- 数据库继续使用 SQLite，降低单机部署复杂度。
- 数据访问使用 MyBatis-Plus，不使用 JPA，避免 SQLite 方言和 Hibernate 自动 DDL 带来的细节摩擦。
- 数据库迁移使用 Flyway，保证后续表结构变更可追踪、可升级。
- MVP 面向一台 Linux 主机、一个后端进程、单人使用。
- 开发环境 SQLite 文件默认放在 `./data/loadtest-platform.db`。
- Linux 部署环境 SQLite 文件默认放在 `/opt/loadtest-platform/data/loadtest-platform.db`。
- 后端只管理平台元数据，不负责把 JMeter 指标写入 InfluxDB。
- JMeter 指标由 JMX 文件内的 Backend Listener 写入 InfluxDB。
- 用户手动从一次成功执行生成测试结果时，平台才查询 InfluxDB 和 Prometheus。
- 前端第一版只支持一个 JMX 步骤，数据库模型预留多个步骤顺序执行能力。
- MVP 不支持强制停止运行中的 JMeter；只允许取消 `scheduled` 和 `pending` 状态的执行。

## 目录结构

```text
backend/
  pom.xml
  src/main/java/com/loadtest/platform/
    LoadTestPlatformApplication.java
    common/ApiResponse.java
    common/NotFoundException.java
    common/GlobalExceptionHandler.java
    config/SQLiteConfig.java
    config/SchedulerConfig.java
    project/
      ProjectController.java
      ProjectService.java
      ProjectMapper.java
      Project.java
      ProjectCreateRequest.java
      ProjectResponse.java
    projectconfig/
      ProjectConfigController.java
      ProjectConfigService.java
      JMeterServer.java
      JMeterServerMapper.java
      ProjectDatasource.java
      ProjectDatasourceMapper.java
      JMeterServerRequest.java
      DatasourceRequest.java
    task/
      TestTaskController.java
      TestTaskService.java
      TestTask.java
      TestTaskStep.java
      TestTaskMapper.java
      TestTaskStepMapper.java
      TestTaskRequest.java
      TestTaskResponse.java
    execution/
      ExecutionController.java
      ExecutionService.java
      ExecutionScheduler.java
      TestExecution.java
      TestExecutionStep.java
      TestExecutionMapper.java
      TestExecutionStepMapper.java
      ExecutionRequest.java
      ExecutionResponse.java
    jmeter/
      JMeterCommandBuilder.java
      JMeterCommand.java
    ssh/
      SshCommandRunner.java
      SshCommandResult.java
    result/
      ResultController.java
      ResultService.java
      AnalysisService.java
      TestResult.java
      TestResultMetric.java
      TestResultMapper.java
      TestResultMetricMapper.java
      ResultResponse.java
    metrics/
      InfluxMetricClient.java
      PrometheusMetricClient.java
      MetricSample.java
    report/
      ReportController.java
      ReportService.java
      TestReport.java
      TestReportMapper.java
      ReportResponse.java
  src/main/resources/
    application.yml
    db/migration/V1__init_schema.sql
  src/test/java/com/loadtest/platform/
frontend/
  package.json
  index.html
  src/
    main.ts
    App.vue
    router.ts
    api/http.ts
    api/projects.ts
    api/tasks.ts
    api/executions.ts
    api/results.ts
    api/reports.ts
    views/ProjectList.vue
    views/ProjectDetail.vue
    views/TaskEditor.vue
    views/ExecutionList.vue
    views/ResultDetail.vue
    views/ReportDetail.vue
    components/StatusTag.vue
    components/MetricTable.vue
docs/
  deployment/linux-single-node.md
```

## 任务 1：后端 Spring Boot 工程骨架

**文件：**
- 新建：`backend/pom.xml`
- 新建：`backend/src/main/java/com/loadtest/platform/LoadTestPlatformApplication.java`
- 新建：`backend/src/main/resources/application.yml`
- 新建：`backend/src/main/java/com/loadtest/platform/common/ApiResponse.java`
- 新建：`backend/src/main/java/com/loadtest/platform/common/GlobalExceptionHandler.java`
- 新建：`backend/src/main/java/com/loadtest/platform/common/NotFoundException.java`

- [ ] **步骤 1：创建 Maven 依赖**

创建 `backend/pom.xml`，核心依赖包括：

```text
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-test
mybatis-plus-boot-starter
sqlite-jdbc
flyway-core
sshd-core
spring-boot-starter-webflux
commonmark
lombok
```

JDK 版本使用 17。

- [ ] **步骤 2：创建应用入口**

创建 `LoadTestPlatformApplication.java`，启用 Spring Boot，并提供标准 `main` 方法。

- [ ] **步骤 3：创建配置文件**

`application.yml` 配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: org.sqlite.JDBC
    url: jdbc:sqlite:./data/loadtest-platform.db
  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **步骤 4：创建统一异常处理**

实现：

```text
NotFoundException
ApiResponse
GlobalExceptionHandler
```

要求：

```text
404 返回资源不存在
400 返回参数错误
500 返回内部错误
```

- [ ] **步骤 5：验证后端启动**

```bash
cd backend
mvn test
mvn spring-boot:run
```

预期：应用启动在 `http://localhost:8080`。

- [ ] **步骤 6：提交**

```bash
git add backend
git commit -m "chore: scaffold Spring Boot backend"
```

## 任务 2：SQLite 表结构与 Flyway 迁移

**文件：**
- 新建：`backend/src/main/resources/db/migration/V1__init_schema.sql`
- 新建：各模块实体类和 Mapper 接口

- [ ] **步骤 1：创建初始化 SQL**

创建 `V1__init_schema.sql`，包含以下表：

```text
projects
jmeter_servers
project_datasources
test_tasks
test_task_steps
test_executions
test_execution_steps
test_results
test_result_metrics
test_reports
```

SQLite 字段类型使用：

```text
INTEGER PRIMARY KEY AUTOINCREMENT
TEXT
REAL
INTEGER
```

时间字段使用 `TEXT` 保存 ISO-8601 字符串。

- [ ] **步骤 2：启用 SQLite 约束和 WAL**

应用启动后需要执行：

```sql
PRAGMA foreign_keys=ON;
PRAGMA journal_mode=WAL;
PRAGMA busy_timeout=5000;
```

可通过 `SQLiteConfig` 在 datasource 初始化后执行。

- [ ] **步骤 3：创建实体和 Mapper**

按表创建实体类和 Mapper，实体使用 Lombok：

```text
@Data
@TableName("projects")
@TableId(type = IdType.AUTO)
```

- [ ] **步骤 4：验证迁移**

```bash
cd backend
mvn spring-boot:run
```

预期：生成 `data/loadtest-platform.db`，并创建所有表。

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/resources/db backend/src/main/java/com/loadtest/platform
git commit -m "feat: add SQLite schema migration"
```

## 任务 3：项目管理 API

**文件：**
- 新建：`ProjectController.java`
- 新建：`ProjectService.java`
- 新建：`ProjectMapper.java`
- 新建：`Project.java`
- 新建：`ProjectCreateRequest.java`
- 新建：`ProjectResponse.java`
- 测试：`ProjectControllerTest.java`

- [ ] **步骤 1：编写接口测试**

覆盖：

```text
创建项目
查询项目列表
查询项目详情
项目名称不能为空
环境名称不能为空
```

- [ ] **步骤 2：实现请求和响应对象**

字段：

```text
name
description
environmentName
status
createdAt
updatedAt
```

- [ ] **步骤 3：实现 Service**

实现：

```text
createProject
listProjects
getProject
```

- [ ] **步骤 4：实现 Controller**

接口：

```text
POST /api/projects
GET /api/projects
GET /api/projects/{projectId}
```

- [ ] **步骤 5：运行测试并提交**

```bash
cd backend
mvn test -Dtest=ProjectControllerTest
git add backend/src
git commit -m "feat: add project management API"
```

## 任务 4：项目运行配置 API

**文件：**
- 新建：`projectconfig/*`
- 测试：`ProjectConfigControllerTest.java`

- [ ] **步骤 1：编写配置接口测试**

覆盖：

```text
保存项目 JMeter 服务器
读取项目 JMeter 服务器
保存 InfluxDB 数据源
保存 Prometheus 数据源
保存 Grafana 数据源
读取项目数据源列表
保存测试环境服务器 instance 列表
```

- [ ] **步骤 2：实现请求对象**

定义：

```text
JMeterServerRequest
DatasourceRequest
```

校验：

```text
sshPort 必须在 1 到 65535 之间
sshAuthType 只能是 password 或 private_key
datasource type 只能是 influxdb、prometheus 或 grafana
baseUrl 必须以 http:// 或 https:// 开头
```

- [ ] **步骤 3：实现 Service**

实现：

```text
upsertJMeterServer
getJMeterServer
upsertDatasource
listDatasources
```

`extraConfigJson` 保存：

```text
InfluxDB measurement 和字段映射
Prometheus instance 显式列表
Grafana dashboard uid
```

- [ ] **步骤 4：实现 Controller**

接口：

```text
PUT /api/projects/{projectId}/jmeter-server
GET /api/projects/{projectId}/jmeter-server
PUT /api/projects/{projectId}/datasources/{datasourceType}
GET /api/projects/{projectId}/datasources
```

- [ ] **步骤 5：运行测试并提交**

```bash
cd backend
mvn test -Dtest=ProjectConfigControllerTest
git add backend/src
git commit -m "feat: add project runtime configuration API"
```

## 任务 5：测试任务模板 API

**文件：**
- 新建：`task/*`
- 测试：`TestTaskControllerTest.java`

- [ ] **步骤 1：编写测试**

覆盖：

```text
创建一个包含单个 JMX 步骤的任务
按项目查询任务列表
拒绝 durationSeconds <= 0
拒绝 threads <= 0
拒绝非 .jmx 文件
```

- [ ] **步骤 2：实现 Service**

实现：

```text
createTaskWithSteps
listTasksByProject
getTask
```

创建任务时同步创建 `test_task_steps`。

- [ ] **步骤 3：实现 Controller**

接口：

```text
POST /api/projects/{projectId}/tasks
GET /api/projects/{projectId}/tasks
GET /api/tasks/{taskId}
```

- [ ] **步骤 4：运行测试并提交**

```bash
cd backend
mvn test -Dtest=TestTaskControllerTest
git add backend/src
git commit -m "feat: add task template API"
```

## 任务 6：JMeter 命令构建与 SSH 执行

**文件：**
- 新建：`jmeter/JMeterCommandBuilder.java`
- 新建：`jmeter/JMeterCommand.java`
- 新建：`ssh/SshCommandRunner.java`
- 新建：`ssh/SshCommandResult.java`
- 测试：`JMeterCommandBuilderTest.java`

- [ ] **步骤 1：编写命令构建测试**

覆盖：

```text
命令包含 -n 和 -t
命令包含 -Jthreads、-Jduration、-Jramp_up
命令总是包含 -j 日志路径
saveJtl 为 true 时才包含 -l
JMX 路径由 scriptDir 和 jmxFile 拼接
```

- [ ] **步骤 2：实现命令构建器**

生成命令：

```bash
{jmeterHome}/bin/jmeter -n -t {scriptDir}/{jmxFile} -Jthreads={threads} -Jduration={durationSeconds} -Jramp_up={rampUpSeconds} -j {logDir}/execution_{executionId}_step_{stepOrder}.log
```

如果 `saveJtl = true`，追加：

```bash
-l {resultDir}/execution_{executionId}_step_{stepOrder}.jtl
```

- [ ] **步骤 3：实现 SSH Runner**

使用 Apache MINA SSHD 执行远程命令，返回：

```text
exitCode
stdout
stderr
```

连接超时 15 秒，命令超时为任务持续时间加 120 秒。

- [ ] **步骤 4：运行测试并提交**

```bash
cd backend
mvn test -Dtest=JMeterCommandBuilderTest
git add backend/src
git commit -m "feat: add JMeter command and SSH runner"
```

## 任务 7：执行 API 与调度器

**文件：**
- 新建：`execution/*`
- 测试：`ExecutionServiceTest.java`

- [ ] **步骤 1：编写执行状态测试**

覆盖：

```text
立即执行创建 pending 记录
定时执行创建 scheduled 记录
pending 可以取消
scheduled 可以取消
running 在 MVP 中不能取消
步骤失败后执行整体 failed
所有步骤成功后执行整体 success
同一项目同一时间只能有一个 running 执行
```

- [ ] **步骤 2：实现执行服务**

实现：

```text
createManualExecution(taskId)
createScheduledExecution(taskId, scheduledAt)
cancelExecution(executionId)
runPendingExecution(executionId)
```

从 `pending` 改为 `running` 时必须在事务中完成，并检查同项目是否已有运行中的执行。

- [ ] **步骤 3：实现调度器**

使用 `@Scheduled(fixedDelay = 10000)` 创建两个任务：

```text
把 scheduledAt <= now 的 scheduled 执行改为 pending
取一个 pending 执行并运行
```

MVP 后端进程内一次只运行一个执行。

- [ ] **步骤 4：实现 Controller**

接口：

```text
POST /api/tasks/{taskId}/executions/manual
POST /api/tasks/{taskId}/executions/scheduled
GET /api/projects/{projectId}/executions
POST /api/executions/{executionId}/cancel
GET /api/executions/{executionId}
```

- [ ] **步骤 5：运行测试并提交**

```bash
cd backend
mvn test -Dtest=ExecutionServiceTest
git add backend/src
git commit -m "feat: add execution scheduling"
```

## 任务 8：测试结果采集与分析

**文件：**
- 新建：`result/*`
- 新建：`metrics/*`
- 测试：`ResultServiceTest.java`

- [ ] **步骤 1：编写结果服务测试**

覆盖：

```text
只能从 success 执行生成结果
InfluxDB 查询失败时生成 failed 结果
Prometheus 部分失败时生成 partial_success 结果
正常指标生成 success 结果
ART P95 阈值能产生 warning 或 critical
CPU max 阈值能产生 warning 或 critical
```

- [ ] **步骤 2：实现指标客户端**

`InfluxMetricClient` 提供：

```text
queryJMeterSummary(datasource, startTime, endTime)
```

返回：

```text
TPS avg / max
ART avg / p90 / p95 / p99
error_rate avg
requests sum
failed_requests sum
```

`PrometheusMetricClient` 提供：

```text
queryServerResourceSummary(datasource, instances, startTime, endTime)
```

返回 CPU、内存、磁盘、IO wait、网络入、网络出、系统负载摘要。

- [ ] **步骤 3：实现分析服务**

阈值：

```text
ART P95 > 1000ms warning，> 3000ms critical
error_rate > 1 warning，> 5 critical
CPU max > 80 warning，> 90 critical
memory max > 80 warning，> 90 critical
IO wait max > 20 warning，> 40 critical
```

`analysisJson` 包含：

```text
overallStatus
riskItems
summary
suggestions
```

- [ ] **步骤 4：实现结果服务**

实现 `generateResultFromExecution(executionId, name)`：

```text
加载 success 执行
查询 InfluxDB 汇总指标
查询 Prometheus 资源指标
保存 test_results
保存 test_result_metrics
返回结果详情
```

- [ ] **步骤 5：实现 Controller**

接口：

```text
POST /api/executions/{executionId}/results
GET /api/projects/{projectId}/results
GET /api/results/{resultId}
```

- [ ] **步骤 6：运行测试并提交**

```bash
cd backend
mvn test -Dtest=ResultServiceTest
git add backend/src
git commit -m "feat: add result collection and analysis"
```

## 任务 9：文字报告生成

**文件：**
- 新建：`report/*`
- 测试：`ReportServiceTest.java`

- [ ] **步骤 1：编写报告测试**

覆盖：

```text
success 结果可以生成报告
报告保存 Markdown 和 HTML
报告包含 MVP 八个章节
failed 结果不能生成报告
partial_success 结果可以生成报告，并提示资源指标不完整
```

- [ ] **步骤 2：实现报告服务**

生成 Markdown，包含：

```text
1. 测试概述
2. 测试配置
3. 执行信息
4. 核心性能指标
5. 服务器资源表现
6. 风险与异常
7. 自动分析结论
8. 后续建议
```

使用 commonmark 转换为 HTML，保存：

```text
reportType = single_result_text
status = success
contentMarkdown
contentHtml
resultIdsJson = [resultId]
```

- [ ] **步骤 3：实现 Controller**

接口：

```text
POST /api/results/{resultId}/reports
GET /api/projects/{projectId}/reports
GET /api/reports/{reportId}
```

- [ ] **步骤 4：运行测试并提交**

```bash
cd backend
mvn test -Dtest=ReportServiceTest
git add backend/src
git commit -m "feat: add text report generation"
```

## 任务 10：前端工程骨架与 API 客户端

**文件：**
- 新建：`frontend/package.json`
- 新建：`frontend/index.html`
- 新建：`frontend/src/main.ts`
- 新建：`frontend/src/App.vue`
- 新建：`frontend/src/router.ts`
- 新建：`frontend/src/api/http.ts`
- 新建：`frontend/src/api/projects.ts`
- 新建：`frontend/src/api/tasks.ts`
- 新建：`frontend/src/api/executions.ts`
- 新建：`frontend/src/api/results.ts`
- 新建：`frontend/src/api/reports.ts`

- [ ] **步骤 1：创建 Vite + Vue 3 工程**

使用：

```text
Vue 3
TypeScript
Vue Router
Element Plus
Axios
```

- [ ] **步骤 2：实现 HTTP 客户端**

`frontend/src/api/http.ts`：

```ts
import axios from "axios";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  timeout: 15000,
});
```

- [ ] **步骤 3：实现 API 模块**

导出函数：

```text
listProjects
createProject
getProject
listTasks
createTask
createManualExecution
createScheduledExecution
cancelExecution
listExecutions
generateResult
listResults
getResult
generateReport
listReports
getReport
```

- [ ] **步骤 4：验证前端启动**

```bash
cd frontend
npm install
npm run dev
```

预期：Vite 开发服务器启动，能看到基础页面。

- [ ] **步骤 5：提交**

```bash
git add frontend
git commit -m "chore: scaffold Vue frontend"
```

## 任务 11：前端核心流程页面

**文件：**
- 新建：`frontend/src/views/ProjectList.vue`
- 新建：`frontend/src/views/ProjectDetail.vue`
- 新建：`frontend/src/views/TaskEditor.vue`
- 新建：`frontend/src/views/ExecutionList.vue`
- 新建：`frontend/src/views/ResultDetail.vue`
- 新建：`frontend/src/views/ReportDetail.vue`
- 新建：`frontend/src/components/StatusTag.vue`
- 新建：`frontend/src/components/MetricTable.vue`
- 修改：`frontend/src/router.ts`
- 修改：`frontend/src/App.vue`

- [ ] **步骤 1：实现项目列表页**

支持：

```text
查询项目列表
创建项目
进入项目详情
```

- [ ] **步骤 2：实现项目详情页**

展示：

```text
项目信息
JMeter 服务器配置
数据源配置
任务列表
执行列表
结果列表
报告列表
```

- [ ] **步骤 3：实现任务编辑页**

表单字段：

```text
任务名称
描述
jmxFile
threads
durationSeconds
rampUpSeconds
saveJtl
jmeterArgsJson
```

- [ ] **步骤 4：实现执行列表页**

操作：

```text
立即执行
定时执行
取消 scheduled 或 pending 执行
从 success 执行生成结果
```

- [ ] **步骤 5：实现结果详情页**

展示：

```text
摘要
分析结论
指标表格
生成报告按钮
```

- [ ] **步骤 6：实现报告详情页**

渲染后端返回的 `contentHtml`。

- [ ] **步骤 7：手工验证 UI 流程**

运行后端和前端，验证：

```text
创建项目
创建任务
创建立即执行
查看执行状态
从成功执行生成测试结果
生成报告
打开报告
```

- [ ] **步骤 8：提交**

```bash
git add frontend
git commit -m "feat: add frontend MVP workflow"
```

## 任务 12：Linux 单机部署

**文件：**
- 新建：`docs/deployment/linux-single-node.md`
- 新建：`backend/scripts/start.sh`
- 新建：`backend/scripts/loadtest-platform.service`

- [ ] **步骤 1：编写部署文档**

覆盖：

```text
安装或确认 JDK 17
安装 Maven 或使用打包好的 jar
安装 Node.js 用于前端构建
创建 /opt/loadtest-platform
复制 backend jar 和 frontend 构建产物
创建 /opt/loadtest-platform/data
设置数据库路径 jdbc:sqlite:/opt/loadtest-platform/data/loadtest-platform.db
使用 systemd 启动后端
使用 nginx 或静态文件服务托管前端
备份 SQLite db 文件
```

- [ ] **步骤 2：添加 systemd 服务模板**

创建 `backend/scripts/loadtest-platform.service`：

```ini
[Unit]
Description=LoadTest Platform Backend
After=network.target

[Service]
WorkingDirectory=/opt/loadtest-platform/backend
Environment=SPRING_DATASOURCE_URL=jdbc:sqlite:/opt/loadtest-platform/data/loadtest-platform.db
ExecStart=/usr/bin/java -jar /opt/loadtest-platform/backend/loadtest-platform.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

- [ ] **步骤 3：提交**

```bash
git add docs/deployment backend/scripts
git commit -m "docs: add single-node deployment guide"
```

## 最终验证

- [ ] **运行后端测试**

```bash
cd backend
mvn test
```

预期：所有后端测试通过。

- [ ] **运行后端打包**

```bash
cd backend
mvn package
```

预期：生成可运行 jar。

- [ ] **运行前端构建**

```bash
cd frontend
npm run build
```

预期：构建成功。

- [ ] **运行完整手工冒烟测试**

```text
创建项目
配置 JMeter 服务器和数据源
创建一个单步骤任务
发起立即执行
确认执行成功或得到可解释的失败
从成功执行生成测试结果
生成文字报告
打开报告详情
```

预期：能从 Web 页面完成 MVP 核心闭环。

## 覆盖范围自查

本计划覆盖：

```text
项目管理
JMeter 服务器配置
InfluxDB / Prometheus / Grafana 数据源配置
单步骤任务创建，并预留多步骤数据模型
立即执行和定时执行
SSH 调用 JMeter
执行状态流转和取消限制
手动生成测试结果
InfluxDB 和 Prometheus 指标采集边界
默认阈值分析
文字报告生成
Vue MVP Web 流程
SQLite 单机部署
```
