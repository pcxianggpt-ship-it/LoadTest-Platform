<script setup lang="ts">
import { isAxiosError } from "axios";
import { ElMessage } from "element-plus";
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getJMeterServer,
  listDatasources,
  testDatasource,
  testJMeterServer,
  upsertDatasource,
  upsertJMeterServer,
  type ConnectionTestResult,
  type Datasource,
  type DatasourcePayload,
  type JMeterServer,
  type JMeterServerPayload,
} from "../api/config";
import { getProject, type Project } from "../api/projects";
import { setCurrentProjectId } from "../stores/currentProject";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId));
const loading = ref(false);
const loadError = ref(false);
const project = ref<Project>();
const sections = ["jmeter", "influxdb", "prometheus", "grafana"] as const;
type ConfigSection = (typeof sections)[number];
const activeSection = ref<ConfigSection>("jmeter");
const testing = reactive<Partial<Record<ConfigSection, boolean>>>({});
const saving = reactive<Partial<Record<ConfigSection, boolean>>>({});
const results = reactive<Partial<Record<ConfigSection, ConnectionTestResult>>>({});
const activeResult = computed(() => results[activeSection.value]);
let detailVersion = 0;

const jmeterForm = reactive({
  name: "默认 JMeter 服务器",
  host: "",
  sshPort: 22,
  sshUsername: "root",
  sshAuthType: "password" as "password" | "private_key",
  sshPasswordEncrypted: "",
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

const grafanaForm = reactive({
  name: "grafana",
  baseUrl: "http://192.168.123.141:3000",
  username: "admin",
  passwordEncrypted: "",
  tokenEncrypted: "",
  extraConfigJson: `{
  "orgId": 1,
  "theme": "light",
  "width": 1200,
  "height": 700,
  "panels": []
}`,
});

const forms = { jmeter: jmeterForm, influxdb: influxForm, prometheus: prometheusForm, grafana: grafanaForm };
const defaults = {
  jmeter: { ...jmeterForm },
  influxdb: { ...influxForm },
  prometheus: { ...prometheusForm },
  grafana: { ...grafanaForm },
};
const kubernetesExample = { k8sPods: [{ namespace: "loadtest", podRegex: "order-api-.*" }] };
const kubernetesExampleJson = JSON.stringify(kubernetesExample, null, 2);

for (const section of sections) {
  watch(forms[section], () => { delete results[section]; });
}

async function loadJMeterServer(id: number): Promise<JMeterServer | undefined> {
  try {
    return await getJMeterServer(id);
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 404) {
      return undefined;
    }
    throw error;
  }
}

function fillConfig(server: JMeterServer | undefined, datasources: Datasource[]) {
  if (server) {
    Object.assign(jmeterForm, {
      name: server.name,
      host: server.host,
      sshPort: server.sshPort,
      sshUsername: server.sshUsername,
      sshAuthType: server.sshAuthType,
      jmeterHome: server.jmeterHome,
      scriptDir: server.scriptDir,
      resultDir: server.resultDir || "",
      logDir: server.logDir,
    });
  }
  const influx = datasources.find((item) => item.type === "influxdb");
  const prometheus = datasources.find((item) => item.type === "prometheus");
  const grafana = datasources.find((item) => item.type === "grafana");
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
  if (grafana) {
    Object.assign(grafanaForm, {
      name: grafana.name,
      baseUrl: grafana.baseUrl,
      username: grafana.username || "",
      extraConfigJson: grafana.extraConfigJson || "",
    });
  }
}

async function loadDetail() {
  const version = ++detailVersion;
  const id = projectId.value;
  loading.value = true;
  loadError.value = false;
  project.value = undefined;
  activeSection.value = "jmeter";
  for (const section of sections) {
    delete results[section];
    delete saving[section];
    delete testing[section];
    Object.assign(forms[section], defaults[section]);
  }
  try {
    const [projectData, serverData, datasourceData] = await Promise.all([
      getProject(id),
      loadJMeterServer(id),
      listDatasources(id),
    ]);
    if (version !== detailVersion) return;
    project.value = projectData;
    setCurrentProjectId(projectId.value);
    fillConfig(serverData, datasourceData);
  } catch {
    if (version === detailVersion) loadError.value = true;
  } finally {
    if (version === detailVersion) loading.value = false;
  }
}

function jmeterPayload(): JMeterServerPayload {
  return {
    ...jmeterForm,
    name: jmeterForm.name.trim(),
    host: jmeterForm.host.trim(),
    sshUsername: jmeterForm.sshUsername.trim(),
    sshPasswordEncrypted: jmeterForm.sshPasswordEncrypted || undefined,
  };
}

function datasourcePayload(type: "influxdb" | "prometheus" | "grafana"): DatasourcePayload {
  const source = type === "influxdb" ? influxForm : type === "prometheus" ? prometheusForm : grafanaForm;
  const payload = { ...source, name: source.name.trim(), baseUrl: source.baseUrl.trim() };
  if (type === "grafana") {
    const { passwordEncrypted, tokenEncrypted, username } = grafanaForm;
    return {
      ...payload,
      username: username.trim(),
      passwordEncrypted: passwordEncrypted || (tokenEncrypted ? "" : undefined),
      tokenEncrypted: tokenEncrypted || (passwordEncrypted ? "" : undefined),
    };
  }
  return type === "influxdb" ? { ...payload, databaseName: influxForm.databaseName.trim() } : payload;
}

function parseExtraConfig(json: string): Record<string, unknown> {
  const value = json.trim() ? JSON.parse(json) : {};
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Expected a JSON object");
  }
  return value;
}

function addKubernetesExample() {
  try {
    const config = parseExtraConfig(prometheusForm.extraConfigJson);
    if ("k8sPods" in config && (!Array.isArray(config.k8sPods) || config.k8sPods.length)) {
      ElMessage.warning("扩展配置已有 k8sPods，请直接编辑现有配置。");
      return;
    }
    prometheusForm.extraConfigJson = JSON.stringify({ ...config, ...kubernetesExample }, null, 2);
  } catch {
    ElMessage.warning("请先修正扩展配置中的 JSON，再添加示例。");
  }
}

function isBusy(section: ConfigSection) {
  return testing[section] || saving[section];
}

async function testConnection() {
  const section = activeSection.value;
  if (isBusy(section)) return;
  const version = detailVersion;
  testing[section] = true;
  delete results[section];
  try {
    const id = projectId.value;
    const result = section === "jmeter"
      ? await testJMeterServer(id, jmeterPayload())
      : await testDatasource(id, section, datasourcePayload(section));
    if (version === detailVersion) results[section] = result;
  } catch (error) {
    if (version === detailVersion) {
      results[section] = {
        connected: false,
        message: isAxiosError(error) && error.code === "ECONNABORTED"
          ? "测试请求超时，请稍后重试。"
          : "无法完成测试，请检查平台连接后重试。",
      };
    }
  } finally {
    if (version === detailVersion) testing[section] = false;
  }
}

async function saveConfig() {
  const section = activeSection.value;
  if (isBusy(section)) return;
  const version = detailVersion;
  const id = projectId.value;
  saving[section] = true;
  try {
    if (section === "jmeter") {
      const payload = jmeterPayload();
      if (!payload.name || !payload.host || !payload.sshUsername || !payload.jmeterHome.trim()
        || !payload.scriptDir.trim() || !payload.logDir.trim()) {
        ElMessage.warning("请填写名称、主机、SSH 用户和必填目录。");
        return;
      }
      await upsertJMeterServer(id, payload);
      if (version !== detailVersion) return;
      jmeterForm.sshPasswordEncrypted = "";
    } else {
      const payload = datasourcePayload(section);
      if (!payload.name || !payload.baseUrl || (section === "influxdb" && !payload.databaseName)) {
        ElMessage.warning("请填写名称、服务地址和必填项。");
        return;
      }
      try {
        parseExtraConfig(payload.extraConfigJson || "");
      } catch {
        ElMessage.warning("扩展配置必须是有效的 JSON 对象。");
        return;
      }
      await upsertDatasource(id, section, payload);
      if (version !== detailVersion) return;
      if (section === "grafana") {
        grafanaForm.passwordEncrypted = "";
        grafanaForm.tokenEncrypted = "";
      }
    }
    ElMessage.success("配置已保存");
  } catch {
    if (version === detailVersion) ElMessage.error("配置保存失败，请检查填写内容后重试。");
  } finally {
    if (version === detailVersion) saving[section] = false;
  }
}

watch(projectId, loadDetail, { immediate: true });
</script>

<template>
  <section v-loading="loading" class="page-section">
    <div class="page-toolbar">
      <div>
        <p v-if="project" class="config-eyebrow">项目配置</p>
        <h1>
          {{ project?.name || "项目配置" }}
          <el-tag v-if="project" type="info" effect="plain">{{ project.environmentName }}</el-tag>
        </h1>
        <p v-if="project?.description">{{ project.description }}</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="router.push('/')">返回项目</el-button>
        <el-button v-if="project" @click="router.push(`/tasks?projectId=${projectId}`)">任务管理</el-button>
        <el-button v-if="project" @click="router.push(`/executions?projectId=${projectId}`)">执行管理</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" title="项目配置加载失败，请重试。" type="error" show-icon :closable="false">
      <el-button size="small" @click="loadDetail">重新加载</el-button>
    </el-alert>

    <section v-if="project" class="settings-block config-card" aria-label="服务配置">
      <el-tabs v-model="activeSection" class="config-tabs">
        <el-tab-pane label="JMeter" name="jmeter">
          <p class="config-description">配置压测执行服务器。测试连接只验证 SSH 登录。</p>
          <el-form label-position="top" :disabled="isBusy('jmeter')" @submit.prevent>
            <h2>连接信息</h2>
            <div class="config-grid">
              <el-form-item label="名称" required><el-input v-model="jmeterForm.name" /></el-form-item>
              <el-form-item label="主机" required><el-input v-model="jmeterForm.host" placeholder="主机名或 IP 地址" /></el-form-item>
              <el-form-item label="SSH 端口">
                <el-input-number v-model="jmeterForm.sshPort" :min="1" :max="65535" />
              </el-form-item>
              <el-form-item label="SSH 用户" required><el-input v-model="jmeterForm.sshUsername" /></el-form-item>
              <el-form-item label="认证方式">
                <el-select v-model="jmeterForm.sshAuthType">
                  <el-option label="密码" value="password" />
                  <el-option label="私钥（暂不支持）" value="private_key" disabled />
                </el-select>
              </el-form-item>
              <el-form-item v-if="jmeterForm.sshAuthType === 'password'" label="SSH 密码">
                <el-input v-model="jmeterForm.sshPasswordEncrypted" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <p v-if="jmeterForm.sshAuthType === 'password'" class="config-hint config-wide">
                主机、端口和账号不变时，留空可沿用已保存的密码。
              </p>
              <p v-else class="config-hint config-wide">当前压测执行仅支持 SSH 密码认证，请切换为密码。</p>
            </div>
            <h2 class="config-group-title">执行目录</h2>
            <div class="config-grid">
              <el-form-item label="JMeter 目录" required><el-input v-model="jmeterForm.jmeterHome" /></el-form-item>
              <el-form-item label="脚本目录" required><el-input v-model="jmeterForm.scriptDir" /></el-form-item>
              <el-form-item label="结果目录"><el-input v-model="jmeterForm.resultDir" /></el-form-item>
              <el-form-item label="日志目录" required><el-input v-model="jmeterForm.logDir" /></el-form-item>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="InfluxDB" name="influxdb">
          <p class="config-description">查询 JMeter 写入的吞吐量、响应时间和错误率等压测指标。</p>
          <el-form label-position="top" :disabled="isBusy('influxdb')" class="config-grid" @submit.prevent>
            <el-form-item label="名称" required><el-input v-model="influxForm.name" /></el-form-item>
            <el-form-item label="服务地址" required><el-input v-model="influxForm.baseUrl" /></el-form-item>
            <el-form-item label="数据库名称" required><el-input v-model="influxForm.databaseName" /></el-form-item>
            <el-form-item label="扩展配置（JSON）" class="config-wide">
              <el-input v-model="influxForm.extraConfigJson" class="code-input" type="textarea" :rows="4" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="Prometheus" name="prometheus">
          <p class="config-description">查询主机和 Kubernetes Pod 的 CPU、内存及网络指标。</p>
          <el-form label-position="top" :disabled="isBusy('prometheus')" class="config-grid" @submit.prevent>
            <el-form-item label="名称" required><el-input v-model="prometheusForm.name" /></el-form-item>
            <el-form-item label="服务地址" required><el-input v-model="prometheusForm.baseUrl" /></el-form-item>
            <el-form-item label="扩展配置（JSON）" class="config-wide">
              <el-input v-model="prometheusForm.extraConfigJson" class="code-input" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" />
            </el-form-item>
          </el-form>
          <details class="config-example">
            <summary>查看 Kubernetes（K8s）配置示例</summary>
            <p>平台后端须能访问 Prometheus。例如，集群内可用 <code>http://prometheus.monitoring.svc.cluster.local:9090</code>，请替换为实际的 Service 地址。</p>
            <pre>{{ kubernetesExampleJson }}</pre>
            <p>将 <code>namespace</code> 改为 Pod 所在的命名空间，<code>podRegex</code> 改为匹配 Pod 名称的正则。只采集 Pod 指标时，可以移除主机配置 <code>instances</code>。</p>
            <el-button size="small" :disabled="isBusy('prometheus')" @click="addKubernetesExample">添加到扩展配置</el-button>
          </details>
        </el-tab-pane>

        <el-tab-pane label="Grafana" name="grafana">
          <p class="config-description">配置报告截图使用的 Grafana 地址、凭据和面板。</p>
          <el-form label-position="top" :disabled="isBusy('grafana')" class="config-grid" @submit.prevent>
            <el-form-item label="名称" required><el-input v-model="grafanaForm.name" /></el-form-item>
            <el-form-item label="服务地址" required><el-input v-model="grafanaForm.baseUrl" /></el-form-item>
            <el-form-item label="用户名"><el-input v-model="grafanaForm.username" /></el-form-item>
            <el-form-item label="密码">
              <el-input v-model="grafanaForm.passwordEncrypted" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="Token">
              <el-input v-model="grafanaForm.tokenEncrypted" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <p class="config-hint config-wide">同时填写时优先使用 Token。地址和用户名不变时，密码与 Token 都留空可沿用已保存的凭据。</p>
            <el-form-item label="扩展配置（JSON）" class="config-wide">
              <el-input v-model="grafanaForm.extraConfigJson" class="code-input" type="textarea" :autosize="{ minRows: 7, maxRows: 14 }" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div v-if="activeResult" class="config-test-result">
        <el-alert :type="activeResult.connected ? 'success' : 'error'" show-icon :closable="false"
          :title="activeResult.connected ? '测试成功' : '测试失败'" :description="activeResult.message" />
      </div>
      <footer class="config-footer">
        <p class="config-hint">测试使用当前填写的配置，不会保存更改。</p>
        <div class="toolbar-actions">
          <el-button :loading="testing[activeSection]" :disabled="saving[activeSection]" @click="testConnection">测试连接</el-button>
          <el-button type="primary" :loading="saving[activeSection]" :disabled="testing[activeSection]" @click="saveConfig">保存配置</el-button>
        </div>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.page-toolbar .config-eyebrow {
  margin: 0 0 var(--space-xs);
  font-size: 13px;
  font-weight: 500;
}
.config-card {
  margin-top: 0;
}
.config-description {
  margin: 0 0 var(--space-lg);
  color: var(--color-body);
}
.config-card h2 {
  margin-bottom: var(--space-base);
}
.config-group-title {
  margin-top: var(--space-lg);
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-base) var(--space-lg);
}
.config-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
}
.config-grid :deep(.el-input-number) {
  width: 100%;
}
.config-wide {
  grid-column: 1 / -1;
}
.config-card .config-hint {
  margin: 0;
  color: var(--color-body);
  font-size: 13px;
}
.config-example {
  margin-top: var(--space-base);
  padding: var(--space-sm) var(--space-base);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-input);
  background: var(--color-canvas-soft);
}
.config-example summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}
.config-example p {
  color: var(--color-body);
}
.config-example pre {
  margin: var(--space-sm) 0;
  padding: var(--space-sm);
  overflow-x: auto;
  border-radius: var(--radius-input);
  background: var(--color-surface);
  font-size: 13px;
}
.config-test-result {
  margin-top: var(--space-lg);
}
.config-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm) var(--space-base);
  margin-top: var(--space-lg);
  padding-top: var(--space-base);
  border-top: 1px solid var(--color-hairline);
}
@media (max-width: 767px) {
  .config-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .config-tabs :deep(.el-tabs__item) {
    padding: 0 var(--space-xs);
  }
}
</style>
