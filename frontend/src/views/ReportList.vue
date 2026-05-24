<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { listProjects, type Project } from "../api/projects";
import { deleteReport, listReports, type TestReport } from "../api/reports";
import StatusTag from "../components/StatusTag.vue";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const projects = ref<Project[]>([]);
const reports = ref<TestReport[]>([]);
const selectedProjectId = ref<number>();

const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value));

async function loadProjects() {
  projects.value = await listProjects();
  const queryProjectId = Number(route.query.projectId);
  selectedProjectId.value = queryProjectId || projects.value[0]?.id;
}

async function loadReports() {
  if (!selectedProjectId.value) {
    reports.value = [];
    return;
  }
  loading.value = true;
  try {
    reports.value = await listReports(selectedProjectId.value);
  } finally {
    loading.value = false;
  }
}

async function removeReport(row: TestReport) {
  try {
    await ElMessageBox.confirm(
      `确认删除报告「${row.title}」？`,
      "删除报告",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await deleteReport(row.id);
  ElMessage.success("报告已删除");
  await loadReports();
}

watch(selectedProjectId, loadReports);

onMounted(async () => {
  await loadProjects();
  await loadReports();
});
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>报告生成</h2>
        <p>{{ selectedProject?.name || "选择项目后查看已生成报告" }}</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="selectedProjectId" placeholder="选择项目" class="project-select">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
        <el-button :disabled="!selectedProjectId" @click="router.push(`/results?projectId=${selectedProjectId}`)">从结果生成</el-button>
      </div>
    </div>

    <el-table :data="reports" border stripe>
      <el-table-column prop="title" label="报告标题" min-width="240" />
      <el-table-column prop="reportType" label="类型" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="生成时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/reports/${row.id}`)">打开</el-button>
          <el-button link type="danger" @click="removeReport(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && selectedProjectId && reports.length === 0" description="当前项目还没有报告" />
  </section>
</template>
