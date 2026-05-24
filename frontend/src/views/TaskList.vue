<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { listProjects, type Project } from "../api/projects";
import { listTasks, type TestTask } from "../api/tasks";
import StatusTag from "../components/StatusTag.vue";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const projects = ref<Project[]>([]);
const tasks = ref<TestTask[]>([]);
const selectedProjectId = ref<number>();

const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value));

async function loadProjects() {
  projects.value = await listProjects();
  const queryProjectId = Number(route.query.projectId);
  selectedProjectId.value = queryProjectId || projects.value[0]?.id;
}

async function loadTasks() {
  if (!selectedProjectId.value) {
    tasks.value = [];
    return;
  }
  loading.value = true;
  try {
    tasks.value = await listTasks(selectedProjectId.value);
  } finally {
    loading.value = false;
  }
}

function createTask() {
  if (!selectedProjectId.value) {
    return;
  }
  router.push(`/tasks/new?projectId=${selectedProjectId.value}`);
}

function runTask(task: TestTask) {
  router.push(`/executions?projectId=${task.projectId}&taskId=${task.id}`);
}

watch(selectedProjectId, loadTasks);

onMounted(async () => {
  await loadProjects();
  await loadTasks();
});
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>任务管理</h2>
        <p>{{ selectedProject?.name || "选择项目后管理压测任务模板" }}</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="selectedProjectId" placeholder="选择项目" class="project-select">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedProjectId" @click="createTask">创建任务</el-button>
      </div>
    </div>

    <el-table :data="tasks" border stripe>
      <el-table-column prop="name" label="任务名称" min-width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="JMX" min-width="160">
        <template #default="{ row }">{{ row.steps?.[0]?.jmxFile || "-" }}</template>
      </el-table-column>
      <el-table-column label="并发" width="90">
        <template #default="{ row }">{{ row.steps?.[0]?.threads || "-" }}</template>
      </el-table-column>
      <el-table-column label="持续时间" width="120">
        <template #default="{ row }">{{ row.steps?.[0]?.durationSeconds || "-" }} 秒</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="runTask(row)">执行</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && selectedProjectId && tasks.length === 0" description="当前项目还没有任务" />
  </section>
</template>
