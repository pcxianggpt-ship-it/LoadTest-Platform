<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  createBusinessDatabase,
  createCleanupPlan,
  deleteBusinessDatabase,
  deleteCleanupPlan,
  listBusinessDatabases,
  listCleanupPlans,
  updateBusinessDatabase,
  updateCleanupPlan,
  type BusinessDatabase,
  type BusinessDatabasePayload,
  type BusinessDatabaseType,
  type CleanupPlan,
  type CleanupPlanPayload,
} from "../api/cleanup";
import { listProjects, type Project } from "../api/projects";
import StatusTag from "../components/StatusTag.vue";

const loading = ref(false);
const projects = ref<Project[]>([]);
const databases = ref<BusinessDatabase[]>([]);
const plans = ref<CleanupPlan[]>([]);
const selectedProjectId = ref<number>();
const databaseDialogVisible = ref(false);
const planDialogVisible = ref(false);
const editingDatabaseId = ref<number>();
const editingPlanId = ref<number>();

const databaseForm = reactive({
  name: "",
  databaseType: "mysql" as BusinessDatabaseType,
  jdbcUrl: "",
  username: "",
  passwordEncrypted: "",
  status: "active" as "active" | "inactive",
});

const planForm = reactive({
  name: "",
  description: "",
  businessDatabaseId: undefined as number | undefined,
  enabled: true,
});

const sqlRows = ref<string[]>([""]);
const databaseMap = computed(() => new Map(databases.value.map((item) => [item.id, item])));

async function loadProjects() {
  projects.value = await listProjects();
  selectedProjectId.value = projects.value[0]?.id;
}

async function loadData() {
  if (!selectedProjectId.value) {
    databases.value = [];
    plans.value = [];
    return;
  }
  loading.value = true;
  try {
    const [databaseData, planData] = await Promise.all([
      listBusinessDatabases(selectedProjectId.value),
      listCleanupPlans(selectedProjectId.value),
    ]);
    databases.value = databaseData;
    plans.value = planData;
  } finally {
    loading.value = false;
  }
}

function resetDatabaseForm() {
  editingDatabaseId.value = undefined;
  databaseForm.name = "";
  databaseForm.databaseType = "mysql";
  databaseForm.jdbcUrl = "";
  databaseForm.username = "";
  databaseForm.passwordEncrypted = "";
  databaseForm.status = "active";
}

function openDatabaseDialog(row?: BusinessDatabase) {
  resetDatabaseForm();
  if (row) {
    editingDatabaseId.value = row.id;
    databaseForm.name = row.name;
    databaseForm.databaseType = row.databaseType;
    databaseForm.jdbcUrl = row.jdbcUrl;
    databaseForm.username = row.username || "";
    databaseForm.passwordEncrypted = row.passwordEncrypted || "";
    databaseForm.status = row.status;
  }
  databaseDialogVisible.value = true;
}

function buildDatabasePayload(): BusinessDatabasePayload {
  return {
    name: databaseForm.name.trim(),
    databaseType: databaseForm.databaseType,
    jdbcUrl: databaseForm.jdbcUrl.trim(),
    username: databaseForm.username.trim() || undefined,
    passwordEncrypted: databaseForm.passwordEncrypted.trim() || undefined,
    status: databaseForm.status,
  };
}

async function submitDatabase() {
  if (!selectedProjectId.value || !databaseForm.name.trim() || !databaseForm.jdbcUrl.trim()) {
    ElMessage.warning("请填写业务库名称和 JDBC URL");
    return;
  }
  if (editingDatabaseId.value) {
    await updateBusinessDatabase(selectedProjectId.value, editingDatabaseId.value, buildDatabasePayload());
    ElMessage.success("业务库已更新");
  } else {
    await createBusinessDatabase(selectedProjectId.value, buildDatabasePayload());
    ElMessage.success("业务库已创建");
  }
  databaseDialogVisible.value = false;
  await loadData();
}

async function removeDatabase(row: BusinessDatabase) {
  try {
    await ElMessageBox.confirm(`删除业务库“${row.name}”后，绑定它的清理方案将不可继续使用，确认删除？`, "删除业务库", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  await deleteBusinessDatabase(row.projectId, row.id);
  ElMessage.success("业务库已删除");
  await loadData();
}

function resetPlanForm() {
  editingPlanId.value = undefined;
  planForm.name = "";
  planForm.description = "";
  planForm.businessDatabaseId = databases.value[0]?.id;
  planForm.enabled = true;
  sqlRows.value = [""];
}

function openPlanDialog(row?: CleanupPlan) {
  resetPlanForm();
  if (row) {
    editingPlanId.value = row.id;
    planForm.name = row.name;
    planForm.description = row.description || "";
    planForm.businessDatabaseId = row.businessDatabaseId;
    planForm.enabled = row.enabled;
    sqlRows.value = row.sqlStatements.length ? [...row.sqlStatements] : [""];
  }
  planDialogVisible.value = true;
}

function buildPlanPayload(): CleanupPlanPayload {
  return {
    name: planForm.name.trim(),
    description: planForm.description.trim() || undefined,
    businessDatabaseId: Number(planForm.businessDatabaseId),
    enabled: planForm.enabled,
    sqlStatements: sqlRows.value.map((item) => item.trim()).filter(Boolean),
  };
}

async function submitPlan() {
  const payload = buildPlanPayload();
  if (!selectedProjectId.value || !payload.name || !payload.businessDatabaseId) {
    ElMessage.warning("请填写方案名称并选择业务库");
    return;
  }
  if (payload.sqlStatements.length === 0) {
    ElMessage.warning("请至少填写一条清理 SQL");
    return;
  }
  if (editingPlanId.value) {
    await updateCleanupPlan(selectedProjectId.value, editingPlanId.value, payload);
    ElMessage.success("清理方案已更新");
  } else {
    await createCleanupPlan(selectedProjectId.value, payload);
    ElMessage.success("清理方案已创建");
  }
  planDialogVisible.value = false;
  await loadData();
}

async function removePlan(row: CleanupPlan) {
  try {
    await ElMessageBox.confirm(`删除清理方案“${row.name}”后，已绑定任务不会再自动清理，确认删除？`, "删除清理方案", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  await deleteCleanupPlan(row.projectId, row.id);
  ElMessage.success("清理方案已删除");
  await loadData();
}

function addSqlRow() {
  sqlRows.value.push("");
}

function removeSqlRow(index: number) {
  sqlRows.value.splice(index, 1);
  if (sqlRows.value.length === 0) {
    sqlRows.value.push("");
  }
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
        <h2>数据清理</h2>
        <p>配置业务数据库和压测成功后的清理 SQL，任务可按需绑定清理方案。</p>
      </div>
      <el-select v-model="selectedProjectId" placeholder="选择项目" class="project-select">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
    </div>

    <div class="cleanup-layout">
      <div class="settings-block">
        <div class="block-toolbar">
          <h3>业务数据库</h3>
          <el-button type="primary" @click="openDatabaseDialog()">新增业务库</el-button>
        </div>
        <el-table :data="databases" border stripe>
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="databaseType" label="类型" width="100" />
          <el-table-column prop="jdbcUrl" label="JDBC URL" min-width="280" show-overflow-tooltip />
          <el-table-column prop="username" label="用户名" width="130" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDatabaseDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="removeDatabase(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="settings-block">
        <div class="block-toolbar">
          <h3>清理方案</h3>
          <el-button type="primary" :disabled="databases.length === 0" @click="openPlanDialog()">新增方案</el-button>
        </div>
        <el-table :data="plans" border stripe>
          <el-table-column prop="name" label="名称" min-width="170" />
          <el-table-column label="业务库" min-width="160">
            <template #default="{ row }">{{ databaseMap.get(row.businessDatabaseId)?.name || "已删除" }}</template>
          </el-table-column>
          <el-table-column label="SQL 数" width="90">
            <template #default="{ row }">{{ row.sqlStatements.length }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.enabled ? 'active' : 'inactive'" /></template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPlanDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="removePlan(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="databaseDialogVisible" :title="editingDatabaseId ? '编辑业务库' : '新增业务库'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="名称" required><el-input v-model="databaseForm.name" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="databaseForm.databaseType">
            <el-option label="MySQL" value="mysql" />
            <el-option label="Oracle" value="oracle" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" required><el-input v-model="databaseForm.jdbcUrl" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="databaseForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="databaseForm.passwordEncrypted" type="password" show-password /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="databaseForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="databaseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDatabase">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialogVisible" :title="editingPlanId ? '编辑清理方案' : '新增清理方案'" width="760px">
      <el-form label-width="110px">
        <el-form-item label="名称" required><el-input v-model="planForm.name" /></el-form-item>
        <el-form-item label="业务库" required>
          <el-select v-model="planForm.businessDatabaseId" filterable>
            <el-option v-for="database in databases" :key="database.id" :label="database.name" :value="database.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="planForm.enabled" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="planForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="清理 SQL" required>
          <div class="sql-editor">
            <div v-for="(_, index) in sqlRows" :key="index" class="sql-row">
              <el-input v-model="sqlRows[index]" type="textarea" :rows="3" placeholder="delete from t_order where test_flag = 1" />
              <el-button type="danger" link @click="removeSqlRow(index)">删除</el-button>
            </div>
            <el-button @click="addSqlRow">新增 SQL</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
