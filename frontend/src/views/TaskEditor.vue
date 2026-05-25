<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { listJmxFiles } from "../api/config";
import { createTask, getTask, updateTask, type TaskPayload } from "../api/tasks";

const route = useRoute();
const router = useRouter();
const routeProjectId = computed(() => Number(route.query.projectId || route.params.projectId));
const taskId = computed(() => Number(route.params.taskId));
const isEditing = computed(() => Number.isFinite(taskId.value) && taskId.value > 0);
const projectId = ref<number>();
const jmxFiles = ref<string[]>([]);
const loadingJmxFiles = ref(false);
const loadingTask = ref(false);
const form = reactive({
  name: "",
  description: "",
  defaultSaveJtl: false,
  stepName: "",
  jmxFile: "",
  threads: 100,
  durationSeconds: 600,
  rampUpSeconds: 60,
  saveJtl: false,
  jmeterArgsJson: "",
});

function backToTasks() {
  router.push(projectId.value ? `/tasks?projectId=${projectId.value}` : "/tasks");
}

async function loadJmxFiles() {
  if (!projectId.value) {
    jmxFiles.value = [];
    return;
  }
  loadingJmxFiles.value = true;
  try {
    jmxFiles.value = await listJmxFiles(projectId.value);
    if (!jmxFiles.value.includes(form.jmxFile)) {
      form.jmxFile = "";
    }
  } catch {
    jmxFiles.value = [];
    form.jmxFile = "";
    ElMessage.warning("请先在项目管理中配置 JMeter 脚本目录");
  } finally {
    loadingJmxFiles.value = false;
  }
}

async function loadTask() {
  if (!isEditing.value) {
    projectId.value = routeProjectId.value || undefined;
    return;
  }
  loadingTask.value = true;
  try {
    const task = await getTask(taskId.value);
    const step = task.steps?.[0];
    projectId.value = task.projectId;
    form.name = task.name;
    form.description = task.description || "";
    form.defaultSaveJtl = task.defaultSaveJtl;
    form.stepName = step?.stepName || "";
    form.jmxFile = step?.jmxFile || "";
    form.threads = step?.threads || 100;
    form.durationSeconds = step?.durationSeconds || 600;
    form.rampUpSeconds = step?.rampUpSeconds || 60;
    form.saveJtl = step?.saveJtl || false;
    form.jmeterArgsJson = step?.jmeterArgsJson || "";
  } finally {
    loadingTask.value = false;
  }
}

function buildPayload(): TaskPayload {
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    defaultSaveJtl: form.defaultSaveJtl,
    step: {
      stepName: form.stepName.trim() || form.name.trim(),
      jmxFile: form.jmxFile.trim(),
      threads: form.threads,
      durationSeconds: form.durationSeconds,
      rampUpSeconds: form.rampUpSeconds,
      saveJtl: form.saveJtl,
      jmeterArgsJson: form.jmeterArgsJson.trim() || undefined,
    },
  };
}

async function submitTask() {
  if (!projectId.value) {
    ElMessage.warning("请先从任务管理选择项目");
    return;
  }
  if (jmxFiles.value.length === 0) {
    ElMessage.warning("脚本目录下没有可选的 JMX 文件");
    return;
  }
  if (!form.name.trim() || !form.jmxFile.trim()) {
    ElMessage.warning("请填写任务名称和 JMX 文件");
    return;
  }
  if (isEditing.value) {
    await updateTask(taskId.value, buildPayload());
    ElMessage.success("任务已更新");
  } else {
    await createTask(projectId.value, buildPayload());
    ElMessage.success("任务已创建");
  }
  backToTasks();
}

onMounted(async () => {
  await loadTask();
  await loadJmxFiles();
});
</script>

<template>
  <section v-loading="loadingTask" class="page-section narrow-page">
    <div class="page-toolbar">
      <div>
        <h2>{{ isEditing ? "编辑测试任务" : "创建测试任务" }}</h2>
        <p>当前 MVP 创建一个 JMX 步骤，后端数据模型已支持多步骤。</p>
      </div>
      <el-button @click="backToTasks">返回任务</el-button>
    </div>

    <el-form label-width="130px">
      <el-form-item label="任务名称" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="步骤名称"><el-input v-model="form.stepName" /></el-form-item>
      <el-form-item label="JMX 文件" required>
        <el-select
          v-model="form.jmxFile"
          :loading="loadingJmxFiles"
          :disabled="loadingJmxFiles || jmxFiles.length === 0"
          placeholder="请选择脚本目录中的 JMX 文件"
          filterable
        >
          <el-option v-for="file in jmxFiles" :key="file" :label="file" :value="file" />
        </el-select>
      </el-form-item>
      <el-form-item label="线程数"><el-input-number v-model="form.threads" :min="1" /></el-form-item>
      <el-form-item label="持续时间"><el-input-number v-model="form.durationSeconds" :min="1" /> <span class="unit-label">秒</span></el-form-item>
      <el-form-item label="预热时间"><el-input-number v-model="form.rampUpSeconds" :min="0" /> <span class="unit-label">秒</span></el-form-item>
      <el-form-item label="保存 JTL"><el-switch v-model="form.saveJtl" /></el-form-item>
      <el-form-item label="JMeter 参数"><el-input v-model="form.jmeterArgsJson" type="textarea" :rows="4" /></el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="loadingTask || loadingJmxFiles || jmxFiles.length === 0" @click="submitTask">保存任务</el-button>
        <el-button @click="backToTasks">取消</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>
