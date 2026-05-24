<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getJMeterServer,
  listDatasources,
  upsertDatasource,
  upsertJMeterServer,
  type Datasource,
  type JMeterServer,
} from "../api/config";
import { getProject, type Project } from "../api/projects";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId));
const loading = ref(false);
const project = ref<Project>();
const jmeterServer = ref<JMeterServer>();
const datasources = ref<Datasource[]>([]);

const jmeterForm = reactive({
  name: "默认 JMeter 服务器",
  host: "",
  sshPort: 22,
  sshUsername: "root",
  sshAuthType: "password" as "password" | "private_key",
  sshPasswordEncrypted: "",
  sshPrivateKeyEncrypted: "",
  jmeterHome: "/opt/apache-jmeter",
  scriptDir: "/opt/jmeter/scripts",
  resultDir: "/opt/jmeter/results",
  logDir: "/opt/jmeter/logs",
});

const influxForm = reactive({
  name: "jmeter-influx",
  baseUrl: "http://127.0.0.1:8086",
  databaseName: "jmeter",
  username: "",
  extraConfigJson: "{\"measurement\":\"jmeter\"}",
});

const prometheusForm = reactive({
  name: "resource-prometheus",
  baseUrl: "http://127.0.0.1:9090",
  username: "",
  extraConfigJson: "{\"instances\":[\"127.0.0.1:9100\"]}",
});

async function safe<T>(request: Promise<T>): Promise<T | undefined> {
  try {
    return await request;
  } catch {
    return undefined;
  }
}

function fillConfig() {
  if (jmeterServer.value) {
    Object.assign(jmeterForm, {
      name: jmeterServer.value.name,
      host: jmeterServer.value.host,
      sshPort: jmeterServer.value.sshPort,
      sshUsername: jmeterServer.value.sshUsername,
      sshAuthType: jmeterServer.value.sshAuthType,
      jmeterHome: jmeterServer.value.jmeterHome,
      scriptDir: jmeterServer.value.scriptDir,
      resultDir: jmeterServer.value.resultDir || "",
      logDir: jmeterServer.value.logDir,
    });
  }
  const influx = datasources.value.find((item) => item.type === "influxdb");
  const prometheus = datasources.value.find((item) => item.type === "prometheus");
  if (influx) {
    Object.assign(influxForm, {
      name: influx.name,
      baseUrl: influx.baseUrl,
      databaseName: influx.databaseName || "",
      username: influx.username || "",
      extraConfigJson: influx.extraConfigJson || "",
    });
  }
  if (prometheus) {
    Object.assign(prometheusForm, {
      name: prometheus.name,
      baseUrl: prometheus.baseUrl,
      username: prometheus.username || "",
      extraConfigJson: prometheus.extraConfigJson || "",
    });
  }
}

async function loadDetail() {
  loading.value = true;
  try {
    const [projectData, serverData, datasourceData] = await Promise.all([
      getProject(projectId.value),
      safe(getJMeterServer(projectId.value)),
      safe(listDatasources(projectId.value)),
    ]);
    project.value = projectData;
    jmeterServer.value = serverData;
    datasources.value = datasourceData || [];
    fillConfig();
  } finally {
    loading.value = false;
  }
}

async function saveJMeterServer() {
  if (!jmeterForm.host.trim()) {
    ElMessage.warning("请填写 JMeter 服务器地址");
    return;
  }
  await upsertJMeterServer(projectId.value, { ...jmeterForm });
  ElMessage.success("JMeter 配置已保存");
  await loadDetail();
}

async function saveDatasource(type: "influxdb" | "prometheus") {
  const source = type === "influxdb" ? influxForm : prometheusForm;
  await upsertDatasource(projectId.value, type, { ...source });
  ElMessage.success("数据源已保存");
  await loadDetail();
}

onMounted(loadDetail);
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <h2>{{ project?.name || "项目详情" }}</h2>
        <p>{{ project?.environmentName }} · {{ project?.description || "暂无描述" }}</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="router.push('/')">返回项目</el-button>
        <el-button type="primary" @click="router.push(`/tasks?projectId=${projectId}`)">任务管理</el-button>
        <el-button type="success" @click="router.push(`/executions?projectId=${projectId}`)">执行管理</el-button>
      </div>
    </div>

    <div class="two-column">
      <section class="settings-block">
        <h3>JMeter 服务器</h3>
        <el-form label-width="110px">
          <el-form-item label="名称"><el-input v-model="jmeterForm.name" /></el-form-item>
          <el-form-item label="主机"><el-input v-model="jmeterForm.host" /></el-form-item>
          <el-form-item label="SSH 端口"><el-input-number v-model="jmeterForm.sshPort" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="SSH 用户"><el-input v-model="jmeterForm.sshUsername" /></el-form-item>
          <el-form-item label="认证方式">
            <el-select v-model="jmeterForm.sshAuthType">
              <el-option label="密码" value="password" />
              <el-option label="私钥" value="private_key" />
            </el-select>
          </el-form-item>
          <el-form-item label="密码"><el-input v-model="jmeterForm.sshPasswordEncrypted" type="password" show-password /></el-form-item>
          <el-form-item label="JMeter 目录"><el-input v-model="jmeterForm.jmeterHome" /></el-form-item>
          <el-form-item label="脚本目录"><el-input v-model="jmeterForm.scriptDir" /></el-form-item>
          <el-form-item label="结果目录"><el-input v-model="jmeterForm.resultDir" /></el-form-item>
          <el-form-item label="日志目录"><el-input v-model="jmeterForm.logDir" /></el-form-item>
          <el-form-item><el-button type="primary" @click="saveJMeterServer">保存服务器配置</el-button></el-form-item>
        </el-form>
      </section>

      <section class="settings-block">
        <h3>数据源</h3>
        <el-form label-width="110px">
          <el-divider content-position="left">InfluxDB</el-divider>
          <el-form-item label="名称"><el-input v-model="influxForm.name" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="influxForm.baseUrl" /></el-form-item>
          <el-form-item label="库名"><el-input v-model="influxForm.databaseName" /></el-form-item>
          <el-form-item label="扩展配置"><el-input v-model="influxForm.extraConfigJson" type="textarea" :rows="2" /></el-form-item>
          <el-form-item><el-button @click="saveDatasource('influxdb')">保存 InfluxDB</el-button></el-form-item>
          <el-divider content-position="left">Prometheus</el-divider>
          <el-form-item label="名称"><el-input v-model="prometheusForm.name" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="prometheusForm.baseUrl" /></el-form-item>
          <el-form-item label="扩展配置"><el-input v-model="prometheusForm.extraConfigJson" type="textarea" :rows="2" /></el-form-item>
          <el-form-item><el-button @click="saveDatasource('prometheus')">保存 Prometheus</el-button></el-form-item>
        </el-form>
      </section>
    </div>
  </section>
</template>
