import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const resultDetailPath = fileURLToPath(new URL("../src/views/ResultDetail.vue", import.meta.url));
const viewSource = await readFile(resultDetailPath, "utf8");

assert.match(viewSource, /Prometheus 资源指标/, "结果详情页必须保留 Prometheus 资源指标分组");
assert.match(viewSource, /K8s Pod 资源指标/, "结果详情页必须保留 K8s Pod 资源指标分组");
assert.match(viewSource, /JMeter 指标/, "结果详情页必须保留 JMeter 指标分组");
assert.doesNotMatch(viewSource, /指标明细/, "结果详情页不应重复展示全量指标明细");
assert.doesNotMatch(
  viewSource,
  /<MetricTable\s+:metrics="result\?\.metrics \|\| \[\]"/,
  "结果详情页不应再用全量指标列表渲染重复表格"
);
