<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  cancelExecution,
  createManualExecution,
  createScheduledExecution,
  deleteExecution,
  listExecutions,
  stopExecution,
  type TestExecution,
} from "../api/executions";
import { listProjects, type Project } from "../api/projects";
import { generateResult } from "../api/results";
import { listTasks, type TestTask } from "../api/tasks";
import StatusTag from "../components/StatusTag.vue";
import { formatDisplayDateTime } from "../utils/dateTime";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const projects = ref<Project[]>([]);
const executions = ref<TestExecution[]>([]);
const tasks = ref<TestTask[]>([]);
const selectedProjectId = ref<number>();
const selectedTaskId = ref<number>();
const taskSearchKeyword = ref("");
const resultDialogVisible = ref(false);
const scheduleDialogVisible = ref(false);
const activeExecution = ref<TestExecution>();
const resultForm = reactive({ name: "" });
const scheduledAt = ref<Date>();

const filteredTasks = computed(() => {
  const keyword = taskSearchKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return tasks.value;
  }
  return tasks.value.filter((item) => taskSearchText(item).includes(keyword));
});

function taskSearchText(item: TestTask) {
  return [
    item.name,
    item.description,
    item.status,
    item.steps?.[0]?.stepName,
    item.steps?.[0]?.jmxFile,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

function filterTasks(query: string) {
  taskSearchKeyword.value = query;
}

function taskMeta(item: TestTask) {
  const step = item.steps?.[0];
  if (!step) {
    return "暂无步骤配置";
  }
  return `${step.jmxFile} · ${step.threads} 并发 · ${step.durationSeconds}s`;
}

async function loadProjects() {
  projects.value = await listProjects();
  const queryProjectId = Number(route.query.projectId || route.params.projectId);
  selectedProjectId.value = queryProjectId || projects.value[0]?.id;
}

async function loadData() {
  if (!selectedProjectId.value) {
    tasks.value = [];
    executions.value = [];
    selectedTaskId.value = undefined;
    return;
  }
  loading.value = true;
  try {
    const [taskData, executionData] = await Promise.all([
      listTasks(selectedProjectId.value),
      listExecutions(selectedProjectId.value),
    ]);
    tasks.value = taskData;
    executions.value = executionData;
    taskSearchKeyword.value = "";
    const routeTaskId = Number(route.query.taskId);
    selectedTaskId.value = routeTaskId || taskData[0]?.id;
  } finally {
    loading.value = false;
  }
}

async function runNow() {
  if (!selectedTaskId.value) {
    ElMessage.warning("请先选择任务");
    return;
  }
  await createManualExecution(selectedTaskId.value);
  ElMessage.success("已创建立即执行");
  await loadData();
}

function openScheduleDialog() {
  if (!selectedTaskId.value) {
    ElMessage.warning("请先选择任务");
    return;
  }
  scheduledAt.value = new Date(Date.now() + 60_000);
  scheduleDialogVisible.value = true;
}

async function submitScheduledRun() {
  if (!selectedTaskId.value || !scheduledAt.value) {
    ElMessage.warning("请选择执行时间");
    return;
  }
  if (scheduledAt.value.getTime() <= Date.now()) {
    ElMessage.warning("定时执行时间必须晚于当前时间");
    return;
  }
  await createScheduledExecution(selectedTaskId.value, { scheduledAt: scheduledAt.value.toISOString() });
  ElMessage.success("已创建定时执行");
  scheduleDialogVisible.value = false;
  await loadData();
}

async function cancel(row: TestExecution) {
  await cancelExecution(row.id);
  ElMessage.success("已取消执行");
  await loadData();
}

async function stop(row: TestExecution) {
  try {
    await ElMessageBox.confirm(
      `停止执行「${row.executionName}」会终止远程 JMeter 进程，确认继续？`,
      "停止执行",
      { type: "warning", confirmButtonText: "停止", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await stopExecution(row.id);
  ElMessage.success("已停止执行");
  await loadData();
}

async function removeExecution(row: TestExecution) {
  try {
    await ElMessageBox.confirm(
      `删除执行「${row.executionName}」会同时删除关联结果和报告，确认继续？`,
      "删除执行",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await deleteExecution(row.id);
  ElMessage.success("执行已删除");
  await loadData();
}

function openResultDialog(row: TestExecution) {
  activeExecution.value = row;
  resultForm.name = `${row.executionName}结果`;
  resultDialogVisible.value = true;
}

async function submitResult() {
  if (!activeExecution.value || !resultForm.name.trim()) {
    return;
  }
  const result = await generateResult(activeExecution.value.id, { name: resultForm.name.trim() });
  ElMessage.success("结果已生成");
  resultDialogVisible.value = false;
  router.push(`/results/${result.id}`);
}

function canCancel(status: string) {
  return status === "scheduled" || status === "pending";
}

function canStop(status: string) {
  return status === "running";
}

watch(selectedProjectId, loadData);

onMounted(async () => {
  await loadProjects();
  await loadData();
});
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>执行管理</h2>
        <p>创建立即执行或定时执行，并从成功执行生成测试结果。</p>
      </div>
      <el-select v-model="selectedProjectId" placeholder="选择项目" class="project-select">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
    </div>

    <div class="action-strip">
      <el-select
        v-model="selectedTaskId"
        placeholder="搜索或选择计划"
        class="task-select"
        filterable
        :filter-method="filterTasks"
        clearable
      >
        <el-option v-for="task in filteredTasks" :key="task.id" :label="task.name" :value="task.id">
          <div class="task-option-title">
            <span>{{ task.name }}</span>
            <el-tag size="small" :type="task.status === 'enabled' ? 'success' : 'info'">{{ task.status }}</el-tag>
          </div>
          <div class="task-option-meta">{{ taskMeta(task) }}</div>
        </el-option>
      </el-select>
      <el-button type="primary" @click="runNow">立即执行</el-button>
      <el-button @click="openScheduleDialog">定时执行</el-button>
    </div>

    <el-table :data="executions" border stripe>
      <el-table-column prop="executionName" label="执行名称" min-width="240" />
      <el-table-column prop="triggerType" label="触发方式" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="计划时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.scheduledAt) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" min-width="200">
        <template #default="{ row }">{{ formatDisplayDateTime(row.endedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canCancel(row.status)" link type="danger" @click="cancel(row)">取消</el-button>
          <el-button v-if="canStop(row.status)" link type="danger" @click="stop(row)">停止</el-button>
          <el-button v-if="row.status === 'success'" link type="primary" @click="openResultDialog(row)">生成结果</el-button>
          <el-button link type="danger" @click="removeExecution(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="resultDialogVisible" title="生成测试结果" width="480px">
      <el-form label-width="90px">
        <el-form-item label="结果名称" required><el-input v-model="resultForm.name" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResult">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scheduleDialogVisible" title="定时执行" width="480px">
      <el-form label-width="90px">
        <el-form-item label="执行时间" required>
          <el-date-picker
            v-model="scheduledAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择年月日时分秒"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitScheduledRun">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>
