# 自动化性能测试平台 MVP 设计

## 1. 背景与目标

本平台用于管理性能测试项目、配置可复用的压测任务、通过 SSH 调用单机 JMeter 执行测试，并在执行完成后由用户手动生成测试结果和文字版测试报告。

MVP 的核心目标是跑通以下闭环：

```text
创建测试项目 -> 创建测试任务 -> 发起测试执行 -> 生成测试结果 -> 生成文字报告
```

第一版使用 Web 页面操作，优先实现稳定可用的最小闭环。Grafana Dashboard 配置会保留在项目中，但 MVP 不导出 Grafana 图片；后续版本再支持图片归档、多个测试结果对比和 Word 报告生成。

## 2. MVP 范围

### 2.1 包含功能

- 创建和管理测试项目。
- 每个项目关联一个 JMeter Dashboard 和一个服务器资源 Dashboard。
- 每个项目配置一台 JMeter 执行服务器和固定 JMX 脚本目录。
- 创建测试任务，任务作为配置模板存在。
- 测试任务数据模型支持多个 JMX 按顺序执行，MVP 页面可先只支持一个 JMX。
- 支持立即执行和定时执行两种模式。
- 通过 SSH 调用单机 JMeter 执行测试。
- JMeter 写入 InfluxDB 由 JMX 脚本自身配置，平台不负责写入逻辑。
- 执行完成后，用户手动选择一次成功执行并生成测试结果。
- 测试结果阶段直接查询 InfluxDB 获取 JMeter 指标，直接查询 Prometheus 获取测试环境全部服务器资源指标。
- 报告模块独立，MVP 基于单个测试结果生成文字版报告。
- 数据模型预留多个测试结果生成一份 Word 报告的能力。

### 2.2 暂不包含功能

- 分布式 JMeter。
- JMeter Agent。
- Jenkins 集成。
- 实时指标曲线。
- 运行中强制停止 JMeter。
- Grafana 图片导出。
- Word/PDF 报告生成。
- 多测试结果对比页面。
- 复杂权限系统。

## 3. 核心对象

平台核心对象分为五层：

```text
测试项目 Project
  |
  v
测试任务 Task Template
  |
  v
测试执行 Execution
  |
  v
测试结果 Result
  |
  v
测试报告 Report
```

### 3.1 测试项目

测试项目是顶层归档单位，用于管理一个系统、业务线或测试环境下的压测能力。

项目中保存：

- 项目名称和描述。
- 测试环境名称。
- JMeter 服务器配置。
- JMX 固定目录。
- InfluxDB 数据源配置。
- Prometheus 数据源配置。
- Grafana JMeter Dashboard 配置。
- Grafana 服务器资源 Dashboard 配置。
- 测试环境服务器范围。

测试环境服务器一般为 6 到 8 台。MVP 建议使用显式服务器列表，避免 Prometheus 标签不规范导致采集范围错误。

### 3.2 测试任务

测试任务只是压测配置模板，不表示某一次真实运行。

任务中保存：

- 任务名称。
- 所属项目。
- 一个或多个 JMX 步骤。
- 每个步骤的并发数、持续时间、Ramp-Up。
- 是否生成 JTL，默认否。
- 测试目标或备注。

MVP 页面可以先只允许一个 JMX 步骤，但数据模型使用 `test_task_steps` 提前支持多个 JMX 顺序执行。

### 3.3 测试执行

测试执行表示某一次真实运行。

执行来源包括：

- 立即执行。
- 定时执行。

执行记录保存：

- 执行模式。
- 计划执行时间。
- 执行状态。
- 实际开始时间。
- 实际结束时间。
- 当前执行步骤。
- SSH 执行日志。
- 错误信息。
- 每个 JMX 步骤的执行结果。

测试任务不保存执行状态、开始时间、结束时间或日志，这些信息全部属于测试执行。

### 3.4 测试结果

测试结果是对一次有效执行的手动指标归档。

用户选择一条成功执行记录，点击生成测试结果后，平台才采集指标。失败、取消或配置错误的执行不会自动产生结果，避免污染历史数据。

MVP 测试结果只保存结构化指标，不保存 Grafana 图片。

### 3.5 测试报告

报告模块独立存在。

MVP 基于一个测试结果生成文字版报告，内容包括测试概述、测试配置、执行信息、核心性能指标、服务器资源表现、风险与异常、自动分析结论和后续建议。

后续版本支持选择多个测试结果，生成完整 Word 报告，并插入 Grafana 图片和指标表格。

## 4. 数据模型

### 4.1 `projects`

```text
id
name
description
environment_name
status
created_at
updated_at
```

### 4.2 `jmeter_servers`

```text
id
project_id
name
host
ssh_port
ssh_username
ssh_auth_type
ssh_password_encrypted
ssh_private_key_encrypted
jmeter_home
script_dir
result_dir
log_dir
status
created_at
updated_at
```

说明：

- MVP 一个项目绑定一台 JMeter 服务器。
- `script_dir` 是固定 JMX 目录。
- `result_dir` 仅在开启 JTL 时使用。
- 密码和私钥必须加密保存。

### 4.3 `project_datasources`

```text
id
project_id
type
name
base_url
database_name
username
password_encrypted
token_encrypted
extra_config_json
status
created_at
updated_at
```

说明：

- `type` 可取 `influxdb`、`prometheus`、`grafana`。
- `extra_config_json` 用于保存 InfluxDB measurement、字段映射、Prometheus 查询模板、Grafana dashboard uid 等配置。

### 4.4 `test_tasks`

```text
id
project_id
name
description
default_save_jtl
status
created_at
updated_at
```

### 4.5 `test_task_steps`

```text
id
task_id
step_order
step_name
jmx_file
threads
duration_seconds
ramp_up_seconds
save_jtl
jmeter_args_json
enabled
created_at
updated_at
```

说明：

- MVP 页面先创建一个步骤。
- 后续多个步骤按 `step_order` 顺序执行。
- `jmeter_args_json` 保存环境、域名、端口和其它 JMeter 变量。

### 4.6 `test_executions`

```text
id
project_id
task_id
execution_name
trigger_type
scheduled_at
status
started_at
ended_at
duration_seconds
current_step_order
ssh_log
error_message
created_by
created_at
updated_at
```

说明：

- `trigger_type` 可取 `manual`、`scheduled`。
- `status` 可取 `pending`、`scheduled`、`running`、`success`、`failed`、`cancelled`。

### 4.7 `test_execution_steps`

```text
id
execution_id
task_step_id
step_order
jmx_file
status
started_at
ended_at
duration_seconds
command
exit_code
ssh_log
error_message
jtl_path
created_at
updated_at
```

说明：

- MVP 单 JMX 也会生成一条步骤记录。
- 如果开启 JTL，`jtl_path` 才有值。

### 4.8 `test_results`

```text
id
project_id
execution_id
name
status
time_range_start
time_range_end
summary_json
analysis_json
created_by
created_at
updated_at
```

说明：

- `status` 可取 `success`、`failed`、`partial_success`。
- `summary_json` 保存整体摘要。
- `analysis_json` 保存自动分析结论。

### 4.9 `test_result_metrics`

```text
id
result_id
source
metric_category
metric_name
target_name
stat_type
value
unit
threshold_value
threshold_status
extra_tags_json
created_at
```

说明：

- `source` 可取 `influxdb`、`prometheus`。
- `metric_category` 可取 `jmeter`、`cpu`、`memory`、`disk_io`、`network`。
- `target_name` 对 JMeter 可表示整体汇总或事务名，对服务器资源可表示主机名或 instance。
- `stat_type` 可取 `avg`、`max`、`min`、`p90`、`p95`、`p99`、`sum`。
- `threshold_status` 可取 `normal`、`warning`、`critical`。

### 4.10 `test_reports`

```text
id
project_id
title
report_type
status
content_markdown
content_html
result_ids_json
created_by
created_at
updated_at
```

说明：

- MVP `result_ids_json` 只保存一个 result id。
- 后续支持多个测试结果。
- `report_type` 可取 `single_result_text`、`multi_result_word`。

## 5. 执行状态流转

### 5.1 执行状态

```text
pending      已创建，等待进入执行队列
scheduled    定时任务，等待到达计划时间
running      正在执行
success      执行成功
failed       执行失败
cancelled    已取消
```

### 5.2 立即执行

```text
用户选择测试任务
  |
  v
点击立即执行
  |
  v
创建 test_executions，status = pending，trigger_type = manual
  |
  v
进入执行队列
  |
  v
status = running，started_at = 当前时间
  |
  v
按 step_order 执行 test_task_steps
  |
  v
全部步骤成功后，status = success，ended_at = 当前时间
```

如果任一步骤失败，当前步骤标记为 `failed`，执行记录标记为 `failed`，后续步骤不再执行。

### 5.3 定时执行

```text
用户选择测试任务和计划执行时间
  |
  v
创建 test_executions，status = scheduled，trigger_type = scheduled
  |
  v
调度器定期扫描 scheduled_at <= 当前时间 的记录
  |
  v
status = pending
  |
  v
进入执行队列
```

MVP 可使用后端内置扫描器，每 10 秒或 30 秒扫描一次。

### 5.4 执行锁

MVP 建议限制同一台 JMeter 服务器同一时间只允许一个执行处于 `running` 状态。

原因是单机 JMeter 同时运行多个压测会互相影响，结果不可信。

### 5.5 取消机制

MVP 最小实现：

- `scheduled` 状态可以取消。
- `pending` 状态可以取消。
- `running` 状态暂不支持强制停止。

后续增强时可记录远程 JMeter 进程 PID，并通过 SSH 执行停止命令。

## 6. JMeter 执行策略

每个执行步骤通过 SSH 拼接并运行 JMeter 命令。

示例：

```bash
jmeter -n \
  -t /opt/jmeter/scripts/order_test.jmx \
  -Jthreads=100 \
  -Jduration=600 \
  -Jramp_up=60 \
  -j /opt/jmeter/logs/execution_123_step_1.log
```

如果开启 JTL：

```bash
-l /opt/jmeter/results/execution_123_step_1.jtl
```

如果不开启 JTL，则不传 `-l`。

JMeter 写入 InfluxDB 由 JMX 脚本内的 Backend Listener 完成，平台不处理写入逻辑。

## 7. 指标采集策略

### 7.1 采集触发

MVP 不自动采集。用户在成功执行记录上点击生成测试结果后，平台才采集。

采集时间范围：

```text
test_executions.started_at ~ test_executions.ended_at
```

可预留以下配置用于裁剪启动和结束噪声：

```text
warmup_ignore_seconds
teardown_ignore_seconds
```

MVP 可先默认不裁剪，或默认忽略前后 10 秒。

### 7.2 InfluxDB JMeter 指标

MVP 保存整体汇总指标：

- TPS 平均值。
- TPS 最大值。
- ART 平均值。
- ART P90。
- ART P95。
- ART P99。
- 错误率。
- 请求总数。
- 失败请求数。

项目级 InfluxDB 配置示例：

```json
{
  "version": "1.x",
  "database": "jmeter",
  "measurement": "jmeter",
  "time_field": "time",
  "transaction_tag": "transaction",
  "status_field": "status",
  "elapsed_field": "avg",
  "count_field": "count",
  "error_count_field": "error_count"
}
```

具体字段需要根据实际 JMeter Backend Listener 写入结构调整。

### 7.3 Prometheus 服务器资源指标

MVP 采集测试环境全部服务器资源摘要：

- CPU 使用率 avg / max。
- 内存使用率 avg / max。
- 磁盘使用率 max。
- IO wait avg / max。
- 网络入流量 avg / max。
- 网络出流量 avg / max。
- 系统负载 avg / max。

服务器范围建议使用显式列表：

```json
{
  "instances": [
    "10.0.0.11:9100",
    "10.0.0.12:9100",
    "10.0.0.13:9100"
  ]
}
```

### 7.4 采集失败处理

- InfluxDB 查询失败：`test_results.status = failed`，记录错误，不生成报告。
- Prometheus 部分服务器查询失败：`test_results.status = partial_success`，保存成功数据，并在报告中提示资源指标不完整。

## 8. 自动分析规则

MVP 使用默认阈值生成结论，后续再做项目级可配置。

建议默认阈值：

```text
ART P95 > 1000ms：warning
ART P95 > 3000ms：critical

错误率 > 1%：warning
错误率 > 5%：critical

CPU max > 80%：warning
CPU max > 90%：critical

内存 max > 80%：warning
内存 max > 90%：critical

IO wait max > 20%：warning
IO wait max > 40%：critical
```

结论生成规则示例：

- TPS 稳定、ART 未超阈值、错误率低、服务器资源正常：当前并发下系统整体稳定，暂无明显性能瓶颈。
- ART 超阈值，但 CPU、内存、IO 均正常：建议优先排查应用内部逻辑、下游依赖、数据库慢查询或线程池配置。
- 多台服务器 CPU 高：可能受到计算资源限制，建议关注应用实例 CPU 使用率和热点接口逻辑。
- 单台服务器 CPU 高，其他服务器较低：可能存在流量分布不均，建议检查负载均衡策略、实例权重或流量分配。
- IO wait 高：可能存在磁盘 IO 压力，建议排查数据库、日志写入、磁盘性能或存储链路。
- 错误率高：建议优先分析失败接口、错误码、应用日志和依赖服务状态。

## 9. 文字报告结构

MVP 报告包含以下章节：

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

报告保存到 `test_reports`：

```text
content_markdown
content_html
result_ids_json
```

MVP 页面直接展示 HTML。

后续 Word 报告可复用测试结果、指标明细和报告内容，再增加图片、表格和对比章节。

## 10. 后续演进

建议后续按以下顺序增强：

1. 支持多个 JMX 步骤在页面配置和顺序执行。
2. 支持运行中取消 JMeter。
3. 支持 Grafana 图片导出并绑定测试结果。
4. 支持多个测试结果对比。
5. 支持生成 Word 报告。
6. 支持项目级阈值配置。
7. 支持更完整的权限、审计和操作日志。

## 11. 待确认事项

- InfluxDB 实际版本是 1.x 还是 2.x。
- JMeter Backend Listener 写入 InfluxDB 的 measurement、tag 和 field 结构。
- Prometheus 中测试环境服务器 instance 的实际命名方式。
- Web 技术栈和后端技术栈。
- 数据库选型，建议优先 PostgreSQL 或 MySQL。
