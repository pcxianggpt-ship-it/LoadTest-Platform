<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { generateReport } from "../api/reports";
import { getResult, type TestResult } from "../api/results";
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
        <el-button v-if="result" @click="router.push(`/projects/${result.projectId}`)">返回项目</el-button>
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
      <p>{{ analysis.summary || "暂无分析结论" }}</p>
      <p class="muted">{{ analysis.suggestions || "暂无后续建议" }}</p>
    </section>

    <section class="settings-block">
      <h3>指标明细</h3>
      <MetricTable :metrics="result?.metrics || []" />
    </section>
  </section>
</template>
