<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getReport, type TestReport } from "../api/reports";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const reportId = computed(() => Number(route.params.reportId));
const loading = ref(false);
const report = ref<TestReport>();

async function loadReport() {
  loading.value = true;
  try {
    report.value = await getReport(reportId.value);
  } finally {
    loading.value = false;
  }
}

onMounted(loadReport);
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>{{ report?.title || "测试报告" }}</h2>
        <p>{{ formatDisplayDateTime(report?.createdAt) }}</p>
      </div>
      <el-button v-if="report" @click="router.push(`/reports?projectId=${report.projectId}`)">返回报告</el-button>
    </div>

    <article class="report-content" v-html="report?.contentHtml || ''"></article>
  </section>
</template>
