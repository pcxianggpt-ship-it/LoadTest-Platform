<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import { createTask } from "../api/tasks";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.query.projectId || route.params.projectId));
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

async function submitTask() {
  if (!projectId.value) {
    ElMessage.warning("请先从任务管理选择项目");
    return;
  }
  if (!form.name.trim() || !form.jmxFile.trim()) {
    ElMessage.warning("请填写任务名称和 JMX 文件");
    return;
  }
  await createTask(projectId.value, {
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
  });
  ElMessage.success("任务已创建");
  router.push(`/tasks?projectId=${projectId.value}`);
}
</script>

<template>
  <section class="page-section narrow-page">
    <div class="page-toolbar">
      <div>
        <h2>创建测试任务</h2>
        <p>当前 MVP 创建一个 JMX 步骤，后端数据模型已支持多步骤。</p>
      </div>
      <el-button @click="router.push(projectId ? `/tasks?projectId=${projectId}` : '/tasks')">返回任务</el-button>
    </div>

    <el-form label-width="130px">
      <el-form-item label="任务名称" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="步骤名称"><el-input v-model="form.stepName" /></el-form-item>
      <el-form-item label="JMX 文件" required><el-input v-model="form.jmxFile" placeholder="order_query.jmx" /></el-form-item>
      <el-form-item label="线程数"><el-input-number v-model="form.threads" :min="1" /></el-form-item>
      <el-form-item label="持续时间"><el-input-number v-model="form.durationSeconds" :min="1" /> <span class="unit-label">秒</span></el-form-item>
      <el-form-item label="预热时间"><el-input-number v-model="form.rampUpSeconds" :min="0" /> <span class="unit-label">秒</span></el-form-item>
      <el-form-item label="保存 JTL"><el-switch v-model="form.saveJtl" /></el-form-item>
      <el-form-item label="JMeter 参数"><el-input v-model="form.jmeterArgsJson" type="textarea" :rows="4" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitTask">保存任务</el-button>
        <el-button @click="router.push(projectId ? `/tasks?projectId=${projectId}` : '/tasks')">取消</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>
