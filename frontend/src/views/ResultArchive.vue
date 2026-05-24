<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { listProjects, type Project } from "../api/projects";
import { generateReport } from "../api/reports";
import { deleteResult, listResults, type TestResult } from "../api/results";
import StatusTag from "../components/StatusTag.vue";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const projects = ref<Project[]>([]);
const results = ref<TestResult[]>([]);
const selectedProjectId = ref<number>();

const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value));

async function loadProjects() {
  projects.value = await listProjects();
  const queryProjectId = Number(route.query.projectId);
  selectedProjectId.value = queryProjectId || projects.value[0]?.id;
}

async function loadResults() {
  if (!selectedProjectId.value) {
    results.value = [];
    return;
  }
  loading.value = true;
  try {
    results.value = await listResults(selectedProjectId.value);
  } finally {
    loading.value = false;
  }
}

async function createReport(row: TestResult) {
  const report = await generateReport(row.id);
  ElMessage.success("报告已生成");
  router.push(`/reports/${report.id}`);
}

async function removeResult(row: TestResult) {
  try {
    await ElMessageBox.confirm(
      `删除结果「${row.name}」会同时删除关联指标和报告，确认继续？`,
      "删除结果",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await deleteResult(row.id);
  ElMessage.success("结果已删除");
  await loadResults();
}

watch(selectedProjectId, loadResults);

onMounted(async () => {
  await loadProjects();
  await loadResults();
});
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>结果归档分析</h2>
        <p>{{ selectedProject?.name || "选择项目后查看压测结果和分析结论" }}</p>
      </div>
      <el-select v-model="selectedProjectId" placeholder="选择项目" class="project-select">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
    </div>

    <el-table :data="results" border stripe>
      <el-table-column prop="name" label="结果名称" min-width="200" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.timeRangeStart) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.timeRangeEnd) }}</template>
      </el-table-column>
      <el-table-column label="归档时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/results/${row.id}`)">分析详情</el-button>
          <el-button v-if="row.status !== 'failed'" link type="success" @click="createReport(row)">生成报告</el-button>
          <el-button link type="danger" @click="removeResult(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && selectedProjectId && results.length === 0" description="当前项目还没有归档结果" />
  </section>
</template>
