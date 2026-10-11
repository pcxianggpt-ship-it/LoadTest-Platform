<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getReport, type TestReport } from "../api/reports";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const reportId = computed(() => Number(route.params.reportId));
const loading = ref(false);
const report = ref<TestReport>();
const pollTimer = ref<ReturnType<typeof setInterval>>();
const isGenerating = computed(() => report.value?.status === "pending" || report.value?.status === "running");

function stopPolling() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
    pollTimer.value = undefined;
  }
}

function pollReport() {
  void loadReport(false);
}

function startPolling() {
  if (!pollTimer.value) {
    pollTimer.value = setInterval(pollReport, 3000);
  }
}

async function loadReport(showLoading = true) {
  if (showLoading) {
    loading.value = true;
  }
  try {
    report.value = await getReport(reportId.value);
    if (isGenerating.value) {
      startPolling();
    } else {
      stopPolling();
    }
  } finally {
    if (showLoading) {
      loading.value = false;
    }
  }
}

onMounted(loadReport);
onUnmounted(stopPolling);
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h1>{{ report?.title || "测试报告" }}</h1>
        <p>{{ formatDisplayDateTime(report?.createdAt) }}</p>
      </div>
      <el-button v-if="report" @click="router.push(`/reports?projectId=${report.projectId}`)">返回报告</el-button>
    </div>

    <el-alert
      v-if="isGenerating"
      title="报告生成中"
      type="info"
      show-icon
      :closable="false"
    />
    <el-result
      v-else-if="report?.status === 'failed'"
      icon="error"
      title="报告生成失败"
      :sub-title="report.errorMessage || '报告生成失败，请稍后重试'"
    />
    <article v-else class="report-content" v-html="report?.contentHtml || ''"></article>
  </section>
</template>
