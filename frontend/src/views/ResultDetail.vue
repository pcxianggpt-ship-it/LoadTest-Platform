<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { generateReport } from "../api/reports";
import { getResult, type ResultMetric, type TestResult } from "../api/results";
import MetricTable from "../components/MetricTable.vue";
import StatusTag from "../components/StatusTag.vue";

const route = useRoute();
const router = useRouter();
const resultId = computed(() => Number(route.params.resultId));
const loading = ref(false);
const result = ref<TestResult>();

const analysis = computed(() => {
  if (!result.value?.analysisJson) {
    return {};
  }
  try {
    return JSON.parse(result.value.analysisJson) as Record<string, unknown>;
  } catch {
    return {};
  }
});

const summary = computed(() => {
  if (!result.value?.summaryJson) {
    return {};
  }
  try {
    return JSON.parse(result.value.summaryJson) as Record<string, unknown>;
  } catch {
    return {};
  }
});

const riskItems = computed(() => {
  const items = analysis.value.riskItems;
  return Array.isArray(items) ? items.map(String) : [];
});

const collectionError = computed(() => {
  const error = summary.value.errorMessage;
  return typeof error === "string" && error.trim() ? error : "";
});

const jmeterMetrics = computed<ResultMetric[]>(() =>
  (result.value?.metrics || []).filter((metric) => metric.source === "influxdb")
);

const prometheusMetrics = computed<ResultMetric[]>(() =>
  (result.value?.metrics || []).filter(
    (metric) => metric.source === "prometheus" && !metric.metricCategory.startsWith("k8s_pod_")
  )
);

const k8sPodMetrics = computed<ResultMetric[]>(() =>
  (result.value?.metrics || []).filter(
    (metric) => metric.source === "prometheus" && metric.metricCategory.startsWith("k8s_pod_")
  )
);

async function loadResult() {
  loading.value = true;
  try {
    result.value = await getResult(resultId.value);
  } finally {
    loading.value = false;
  }
}

async function createReport() {
  if (!result.value) {
    return;
  }
  const report = await generateReport(result.value.id);
  ElMessage.success("报告已生成");
  router.push(`/reports/${report.id}`);
}

onMounted(loadResult);
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>{{ result?.name || "测试结果" }}</h2>
        <p>执行编号：{{ result?.executionId || "-" }}</p>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="result" @click="router.push(`/results?projectId=${result.projectId}`)">返回结果</el-button>
        <el-button v-if="result?.status !== 'failed'" type="primary" @click="createReport">生成报告</el-button>
      </div>
    </div>

    <div v-if="result" class="summary-grid">
      <el-statistic title="指标数量" :value="Number(summary.metricCount || result.metrics.length)" />
      <div class="summary-cell">
        <span>结果状态</span>
        <StatusTag :status="result.status" />
      </div>
      <div class="summary-cell">
        <span>整体分析</span>
        <StatusTag :status="String(analysis.overallStatus || 'normal')" />
      </div>
    </div>

    <section class="settings-block">
      <h3>分析结论</h3>
      <el-alert
        v-if="collectionError"
        class="result-alert"
        type="warning"
        :title="collectionError"
        show-icon
        :closable="false"
      />
      <p>{{ analysis.summary || "暂无分析结论" }}</p>
      <p class="muted">{{ analysis.suggestions || "暂无后续建议" }}</p>
      <div v-if="riskItems.length" class="risk-list">
        <el-tag v-for="item in riskItems" :key="item" type="warning" effect="plain">{{ item }}</el-tag>
      </div>
    </section>

    <section class="settings-block">
      <h3>Prometheus 资源指标</h3>
      <MetricTable :metrics="prometheusMetrics" />
    </section>

    <section class="settings-block">
      <h3>K8s Pod 资源指标</h3>
      <MetricTable :metrics="k8sPodMetrics" />
    </section>

    <section class="settings-block">
      <h3>JMeter 指标</h3>
      <MetricTable :metrics="jmeterMetrics" />
    </section>

    <section class="settings-block">
      <h3>指标明细</h3>
      <MetricTable :metrics="result?.metrics || []" />
    </section>
  </section>
</template>
