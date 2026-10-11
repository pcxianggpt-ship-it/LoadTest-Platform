<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { createProject, deleteProject, listProjects, type Project } from "../api/projects";
import StatusTag from "../components/StatusTag.vue";
import { formatDisplayDateTime } from "../utils/dateTime";

const router = useRouter();
const loading = ref(false);
const loadError = ref("");
const dialogVisible = ref(false);
const projects = ref<Project[]>([]);
const form = reactive({
  name: "",
  description: "",
  environmentName: "test",
});

async function loadProjects() {
  loading.value = true;
  loadError.value = "";
  try {
    projects.value = await listProjects();
  } catch {
    loadError.value = "项目列表加载失败，请检查连接后重试。";
  } finally {
    loading.value = false;
  }
}

async function submitProject() {
  if (!form.name.trim() || !form.environmentName.trim()) {
    ElMessage.warning("请填写项目名称和环境名称");
    return;
  }
  const project = await createProject({
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    environmentName: form.environmentName.trim(),
  });
  ElMessage.success("项目已创建");
  dialogVisible.value = false;
  Object.assign(form, { name: "", description: "", environmentName: "test" });
  await loadProjects();
  router.push(`/projects/${project.id}`);
}

async function removeProject(project: Project) {
  try {
    await ElMessageBox.confirm(
      `删除项目「${project.name}」会同时删除它的任务、执行、结果和报告，确认继续？`,
      "删除项目",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await deleteProject(project.id);
  ElMessage.success("项目已删除");
  await loadProjects();
}

onMounted(loadProjects);
</script>

<template>
  <section class="page-section">
    <div class="page-toolbar">
      <div>
        <h1>项目管理 <span v-if="!loading && !loadError" class="title-count">{{ projects.length }}</span></h1>
        <p>从这里创建测试项目，并进入完整压测流程。</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">创建项目</el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="result-alert">
      <el-button size="small" @click="loadProjects">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="projects" border stripe>
      <el-table-column prop="name" label="项目名称" min-width="180" />
      <el-table-column prop="environmentName" label="环境" width="140" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="220">
        <template #default="{ row }">{{ formatDisplayDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/projects/${row.id}`)">配置</el-button>
          <el-button link type="danger" @click="removeProject(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading && !loadError" description="创建第一个项目，开始配置压测流程" :image-size="72">
          <el-button @click="dialogVisible = true">创建项目</el-button>
        </el-empty>
        <span v-else>{{ loadError ? "暂无可显示的项目" : "正在加载项目…" }}</span>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" title="创建项目" width="520px">
      <el-form label-width="92px">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="例如：订单系统压测" />
        </el-form-item>
        <el-form-item label="环境名称" required>
          <el-input v-model="form.environmentName" placeholder="例如：test / staging" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProject">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
