# 异步报告生成与 Grafana 自动截图设计

## 背景

当前平台已经支持从成功执行生成结果，也支持在结果详情页手动导出 Grafana 单 panel 图片；报告生成会把已存在的结果图片嵌入报告。新的目标是在生成报告时，自动根据执行管理记录的开始和结束时间，从 Grafana 截取 JMeter 与 Node Exporter Dashboard 图片，并避免在“结果归档分析”阶段提前产生无用截图。

同时，当前前端各模块进入时会默认选择第一个项目，导致用户在任务管理选择项目 B 后，切换到执行管理、结果或报告模块时回到项目 A。需要让“当前项目”跨模块保持一致。

## 目标

1. 报告生成改为异步流程，避免 Grafana 截图和 HTML 渲染阻塞用户请求。
2. 仅在用户生成报告时自动截图，不在结果生成时截图。
3. 报告记录包含生成状态与失败原因，用户可在列表或详情页看到进度和错误。
4. Grafana 截图使用结果关联执行的 `startedAt` 和 `endedAt` 时间范围。
5. 支持按项目配置 JMeter 与 Node Exporter 的多个 Grafana panel。
6. 前端跨任务管理、执行管理、结果归档、报告生成、数据清理模块保持最近选择的项目。

## 非目标

1. 不做 Grafana 截图的长期重试队列。失败原因落库，用户可重新发起生成。
2. 不在执行结束后自动创建报告。
3. 不引入新的前端状态库。
4. 不把已失败或未完成结果纳入报告生成。

## 后端设计

### 数据模型

为 `test_reports` 增加以下字段：

- `error_message TEXT`：报告生成失败原因。
- `started_at TEXT`：后台任务开始时间。
- `finished_at TEXT`：后台任务结束时间。

报告状态使用：

- `pending`：报告记录已创建，等待后台生成。
- `running`：后台正在截图和渲染。
- `success`：生成成功。
- `failed`：生成失败，`error_message` 保存原因。

`ReportResponse` 增加对应字段，前端可直接展示。

### API 行为

`POST /api/results/{resultId}/reports` 不再同步生成完整报告，而是：

1. 校验 result 存在且不是 `failed`。
2. 创建 `pending` 报告，写入 `project_id`、标题、`result_ids_json`、创建时间。
3. 立即返回该报告记录。

`GET /api/reports/{reportId}` 和 `GET /api/projects/{projectId}/reports` 返回当前状态、失败原因和内容。

### 后台调度

新增 `ReportScheduler`，按固定间隔调用 `ReportService.runOnePendingReport()`。

处理流程：

1. 若已有 `running` 报告，暂不启动下一条，避免多个 Grafana 渲染任务并发压垮服务。
2. 选择最早一条 `pending` 报告，标记为 `running`，写入 `started_at`。
3. 解析 `result_ids_json`，当前只支持单结果报告。
4. 自动导出 Grafana 图片。
5. 读取指标与图片，构建 Markdown 和 HTML。
6. 成功则写入内容并标记 `success`，写入 `finished_at`。
7. 捕获异常后标记 `failed`，写入 `error_message` 与 `finished_at`。

### Grafana 自动截图

在 `ResultImageService` 中新增批量自动导出方法，例如 `exportConfiguredGrafanaImages(Long resultId)`。

该方法复用现有 `GrafanaImageClient.renderPanel` 和图片保存逻辑。时间范围来自 `TestResult.timeRangeStart` 与 `TestResult.timeRangeEnd`，也就是执行管理模块记录的启动和退出时间。

项目的 Grafana datasource 使用 `type = grafana`。`extraConfigJson` 支持如下结构：

```json
{
  "orgId": 1,
  "theme": "light",
  "width": 1200,
  "height": 700,
  "panels": [
    {
      "title": "JMeter 总览",
      "dashboardUid": "jmeter-dashboard-uid",
      "dashboardSlug": "jmeter",
      "panelId": 1,
      "imageType": "jmeter"
    },
    {
      "title": "Node Exporter CPU",
      "dashboardUid": "node-exporter-dashboard-uid",
      "dashboardSlug": "node-exporter",
      "panelId": 2,
      "imageType": "node_exporter"
    }
  ]
}
```

字段规则：

- panel 级配置优先于全局配置。
- `dashboardUid` 和 `panelId` 必填。
- `dashboardSlug` 为空时使用 `_`。
- `theme` 默认 `light`。
- `width` 默认 1200，`height` 默认 700。
- `imageType` 默认 `grafana_panel`，用于区分 JMeter 与 Node Exporter 截图。

为避免同一报告重复截图，自动导出前删除该 result 现有的自动截图，或按 `imageType` 与 panel 配置去重。推荐实现为仅删除 `imageType` 属于自动生成集合的图片，保留用户在结果详情中手动导出的图片。

### 错误处理

以下情况报告置为 `failed`：

- Grafana datasource 不存在。
- Grafana 配置 JSON 无法解析。
- 未配置任何 panel。
- Grafana 返回空内容或非图片内容。
- 保存图片失败。
- Markdown/HTML 渲染失败。

失败原因写入 `error_message`，日志记录完整异常，接口返回给前端简短原因。

## 前端设计

### 报告生成体验

结果详情页点击“生成报告”后：

1. 调用生成接口，得到 `pending` 报告。
2. 跳转到报告详情页。
3. 报告详情页若状态为 `pending` 或 `running`，显示生成中状态并定时轮询。
4. 状态为 `success` 时展示报告 HTML。
5. 状态为 `failed` 时展示错误原因。

报告列表保留状态列，新增失败原因展示；`pending/running` 报告可以打开详情页查看进度。

### Grafana 配置入口

项目详情页在数据源区域补充 Grafana 配置表单：

- Grafana 地址，例如 `http://192.168.123.141:3000`。
- 用户名、密码或 Token。
- 扩展配置 JSON，用于维护 dashboard UID、panelId、尺寸和主题。

初始默认可填：

```json
{
  "orgId": 1,
  "theme": "light",
  "width": 1200,
  "height": 700,
  "panels": []
}
```

后续可再做可视化 panel 编辑器，本次先保持 JSON 配置，贴合现有 InfluxDB 和 Prometheus 配置方式。

### 当前项目保持

新增轻量 composable，例如 `frontend/src/stores/currentProject.ts`：

- 从 `localStorage` 读取最近选择的项目 ID。
- 提供 `resolveProjectId(projects, routeProjectId)`：
  - 优先使用 URL 中的 `projectId`。
  - 其次使用 `localStorage` 中仍存在于项目列表的 ID。
  - 最后使用项目列表第一项。
- 提供 `setCurrentProjectId(id)`，各模块选择项目时写入。

任务管理、执行管理、结果归档、报告生成、数据清理统一使用该逻辑。项目详情页进入时也同步当前项目 ID。

## 测试计划

后端测试：

1. 报告生成接口创建 `pending` 报告并立即返回。
2. 后台任务成功时，报告状态从 `pending` 到 `running` 再到 `success`。
3. Grafana 配置缺失或渲染失败时，报告状态为 `failed` 且写入 `error_message`。
4. 自动截图使用 result 的时间范围。
5. 报告内容包含自动截图。

前端测试：

1. 从结果详情生成报告后跳转到报告详情页。
2. `pending/running/failed/success` 状态展示正确。
3. 项目选择在任务、执行、结果、报告、清理模块之间保持一致。
4. URL 中带 `projectId` 时优先使用 URL。

手工验证：

1. 在 WSL 或本机访问 `http://192.168.123.141:3000`，使用 `admin/admin` 验证 Grafana 登录与 render 接口。
2. 配置真实 JMeter 与 Node Exporter panel 后，生成报告并确认图片时间范围与执行开始/结束时间一致。

## 风险与缓解

- Grafana image renderer 未安装或不可用：报告失败并记录明确错误，日志保留完整响应信息。
- Dashboard UID 或 panelId 配错：报告失败，错误原因提示 Grafana 返回非图片或空内容。
- 截图耗时较长：异步生成避免阻塞用户，且单任务串行执行降低 Grafana 压力。
- 重复生成报告产生多组图片：自动截图只发生在报告任务内，并可删除或覆盖该 result 下自动生成的旧图片。
- 前端项目 ID 缓存失效：初始化时校验缓存 ID 是否仍在项目列表中，不存在则回退到第一个项目。
