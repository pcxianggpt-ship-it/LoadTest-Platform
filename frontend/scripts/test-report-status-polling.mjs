import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const reportApiPath = fileURLToPath(new URL("../src/api/reports.ts", import.meta.url));
const reportDetailPath = fileURLToPath(new URL("../src/views/ReportDetail.vue", import.meta.url));
const reportListPath = fileURLToPath(new URL("../src/views/ReportList.vue", import.meta.url));
const resultDetailPath = fileURLToPath(new URL("../src/views/ResultDetail.vue", import.meta.url));

const [reportApiSource, reportDetailSource, reportListSource, resultDetailSource] = await Promise.all([
  readFile(reportApiPath, "utf8"),
  readFile(reportDetailPath, "utf8"),
  readFile(reportListPath, "utf8"),
  readFile(resultDetailPath, "utf8"),
]);

assert.match(
  reportApiSource,
  /status:\s*"pending"\s*\|\s*"running"\s*\|\s*"success"\s*\|\s*"failed"\s*\|\s*string/,
  "TestReport.status 必须包含 pending/running/success/failed 联合类型"
);
assert.match(reportApiSource, /errorMessage\?:\s*string/, "TestReport 必须包含 errorMessage");
assert.match(reportApiSource, /startedAt\?:\s*string/, "TestReport 必须包含 startedAt");
assert.match(reportApiSource, /finishedAt\?:\s*string/, "TestReport 必须包含 finishedAt");

assert.match(resultDetailSource, /报告任务已创建/, "生成报告后必须提示任务已创建");
assert.match(resultDetailSource, /router\.push\(`\/reports\/\$\{report\.id\}`\)/, "生成报告后必须跳转报告详情");

assert.match(reportDetailSource, /报告生成中/, "报告详情必须展示生成中状态");
assert.match(reportDetailSource, /setInterval\([^,]+,\s*3000\)/s, "报告详情必须每 3 秒轮询");
assert.match(reportDetailSource, /onUnmounted/, "报告详情卸载时必须清理轮询 timer");
assert.match(reportDetailSource, /errorMessage/, "报告详情必须展示失败错误信息");
assert.match(reportDetailSource, /contentHtml/, "报告详情成功时必须展示报告 HTML");

assert.match(reportListSource, /el-tooltip[\s\S]*row\.errorMessage/, "报告列表状态列 tooltip 必须展示 errorMessage");
assert.match(reportListSource, /router\.push\(`\/reports\/\$\{row\.id\}`\)/, "报告列表所有报告都必须能打开详情页");
