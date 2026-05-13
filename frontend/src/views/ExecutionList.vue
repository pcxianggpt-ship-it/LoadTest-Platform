<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  cancelExecution,
  createManualExecution,
  createScheduledExecution,
  listExecutions,
  type TestExecution,
} from "../api/executions";
import { generateResult } from "../api/results";
import { listTasks, type TestTask } from "../api/tasks";
import StatusTag from "../components/StatusTag.vue";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId));
const loading = ref(false);
const executions = ref<TestExecution[]>([]);
const tasks = ref<TestTask[]>([]);
const selectedTaskId = ref<number>();
const resultDialogVisible = ref(false);
const activeExecution = ref<TestExecution>();
const resultForm = reactive({ name: "" });

async function loadData() {
  loading.value = true;
  try {
    const [taskData, executionData] = await Promise.all([listTasks(projectId.value), listExecutions(projectId.value)]);
    tasks.value = taskData;
    executions.value = executionData;
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

async function scheduleRun() {
  if (!selectedTaskId.value) {
    ElMessage.warning("请先选择任务");
    return;
  }
  const scheduledAt = new Date(Date.now() + 60_000).toISOString();
  await createScheduledExecution(selectedTaskId.value, { scheduledAt });
  ElMessage.success("已创建定时执行");
  await loadData();
}

async function cancel(row: TestExecution) {
  await cancelExecution(row.id);
  ElMessage.success("已取消执行");
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

onMounted(loadData);
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>执行管理</h2>
        <p>创建立即执行或定时执行，并从成功执行生成测试结果。</p>
      </div>
      <el-button @click="router.push(`/projects/${projectId}`)">返回项目</el-button>
    </div>

    <div class="action-strip">
      <el-select v-model="selectedTaskId" placeholder="选择任务" class="task-select">
        <el-option v-for="task in tasks" :key="task.id" :label="task.name" :value="task.id" />
      </el-select>
      <el-button type="primary" @click="runNow">立即执行</el-button>
      <el-button @click="scheduleRun">一分钟后执行</el-button>
    </div>

    <el-table :data="executions" border stripe>
      <el-table-column prop="executionName" label="执行名称" min-width="240" />
      <el-table-column prop="triggerType" label="触发方式" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column prop="scheduledAt" label="计划时间" min-width="200" />
      <el-table-column prop="startedAt" label="开始时间" min-width="200" />
      <el-table-column prop="endedAt" label="结束时间" min-width="200" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canCancel(row.status)" link type="danger" @click="cancel(row)">取消</el-button>
          <el-button v-if="row.status === 'success'" link type="primary" @click="openResultDialog(row)">生成结果</el-button>
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
  </section>
</template>
