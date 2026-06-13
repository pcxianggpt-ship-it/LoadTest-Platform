<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { generateReport } from "../api/reports";
import {
  exportGrafanaImage,
  getResult,
  type ExportGrafanaImagePayload,
  type ResultMetric,
  type TestResult,
} from "../api/results";
import MetricTable from "../components/MetricTable.vue";
import StatusTag from "../components/StatusTag.vue";

const route = useRoute();
const router = useRouter();
const resultId = computed(() => Number(route.params.resultId));
const loading = ref(false);
const exportingImage = ref(false);
const imageDialogVisible = ref(false);
const result = ref<TestResult>();
const grafanaForm = ref<ExportGrafanaImagePayload>({
  title: "",
  dashboardUid: "",
  dashboardSlug: "",
  panelId: 1,
  width: 1200,
  height: 700,
  theme: "light",
});

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

const images = computed(() => result.value?.images || []);

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

async function submitGrafanaImage() {
  if (!result.value) {
    return;
  }
  exportingImage.value = true;
  try {
    await exportGrafanaImage(result.value.id, {
      ...grafanaForm.value,
      title: grafanaForm.value.title || `Grafana Panel ${grafanaForm.value.panelId}`,
      dashboardUid: grafanaForm.value.dashboardUid || undefined,
      dashboardSlug: grafanaForm.value.dashboardSlug || undefined,
      theme: grafanaForm.value.theme || "light",
    });
    ElMessage.success("Grafana 图片已归档");
    imageDialogVisible.value = false;
    await loadResult();
  } finally {
    exportingImage.value = false;
  }
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
        <el-button v-if="result && result.status !== 'failed'" @click="imageDialogVisible = true">导出 Grafana 图片</el-button>
        <el-button v-if="result && result.status !== 'failed'" type="primary" @click="createReport">生成报告</el-button>
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
      <div class="block-toolbar">
        <h3>Grafana 图片归档</h3>
        <el-button v-if="result && result.status !== 'failed'" size="small" @click="imageDialogVisible = true">导出图片</el-button>
      </div>
      <el-empty v-if="!images.length" description="暂无归档图片" />
      <div v-else class="grafana-image-grid">
        <figure v-for="image in images" :key="image.id" class="grafana-image-card">
          <img :src="image.downloadUrl" :alt="image.title" />
          <figcaption>
            <strong>{{ image.title }}</strong>
            <span>Panel {{ image.panelId }} · {{ image.width }}x{{ image.height }}</span>
          </figcaption>
        </figure>
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

    <el-dialog v-model="imageDialogVisible" title="导出 Grafana 图片" width="520px">
      <el-form label-width="120px" :model="grafanaForm">
        <el-form-item label="图片标题">
          <el-input v-model="grafanaForm.title" placeholder="例如：TPS 趋势" />
        </el-form-item>
        <el-form-item label="Dashboard UID">
          <el-input v-model="grafanaForm.dashboardUid" placeholder="为空时使用项目 Grafana 配置" />
        </el-form-item>
        <el-form-item label="Dashboard Slug">
          <el-input v-model="grafanaForm.dashboardSlug" placeholder="为空时使用项目 Grafana 配置" />
        </el-form-item>
        <el-form-item label="Panel ID" required>
          <el-input-number v-model="grafanaForm.panelId" :min="1" />
        </el-form-item>
        <el-form-item label="图片尺寸">
          <div class="image-size-row">
            <el-input-number v-model="grafanaForm.width" :min="320" :max="3840" />
            <span>x</span>
            <el-input-number v-model="grafanaForm.height" :min="240" :max="2160" />
          </div>
        </el-form-item>
        <el-form-item label="主题">
          <el-segmented v-model="grafanaForm.theme" :options="['light', 'dark']" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imageDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exportingImage" @click="submitGrafanaImage">导出</el-button>
      </template>
    </el-dialog>

  </section>
</template>
