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
const prometheusHelpVisible = ref(false);
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
  extraConfigJson: `{
  "instances": ["127.0.0.1:9100", "192.0.2.11:9100", "192.0.2.12:9100"]
}`,
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
const kubernetesExampleJson = `{
  "k8sPods": [
    { "namespace": "als9", "podRegex": "*" },
    { "namespace": "kubemate-system", "podRegex": "traefik.*" },
    { "namespace": "redis-sentinel", "podRegex": "*" }
  ]
}`;
const prometheusScrapeExample = `scrape_configs:
  - job_name: kubernetes-cadvisor
    scrape_interval: 15s
    scheme: https
    kubernetes_sd_configs:
      - role: node
        api_server: https://kube-api.example.com:6443
        authorization:
          credentials_file: /etc/prometheus/kubernetes/token
        tls_config:
          ca_file: /etc/prometheus/kubernetes/ca.crt
    authorization:
      credentials_file: /etc/prometheus/kubernetes/token
    tls_config:
      ca_file: /etc/prometheus/kubernetes/ca.crt
    relabel_configs:
      - target_label: __address__
        replacement: kube-api.example.com:6443
      - source_labels: [__meta_kubernetes_node_name]
        target_label: __metrics_path__
        replacement: /api/v1/nodes/\${1}/proxy/metrics/cadvisor
      - source_labels: [__meta_kubernetes_node_name]
        target_label: node`;

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
    prometheusForm.extraConfigJson = JSON.stringify({ ...config, ...parseExtraConfig(kubernetesExampleJson) }, null, 2);
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
          <div class="prometheus-intro">
            <p class="config-description">查询主机和 Kubernetes Pod 的 CPU、内存及网络指标。</p>
            <el-button size="small" @click="prometheusHelpVisible = true">Kubernetes 配置帮助</el-button>
          </div>
          <el-form label-position="top" :disabled="isBusy('prometheus')" class="config-grid" @submit.prevent>
            <el-form-item label="名称" required><el-input v-model="prometheusForm.name" /></el-form-item>
            <el-form-item label="服务地址" required><el-input v-model="prometheusForm.baseUrl" /></el-form-item>
            <el-form-item label="扩展配置（JSON）" class="config-wide">
              <el-input v-model="prometheusForm.extraConfigJson" class="code-input" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" />
              <p class="config-hint">主机监控时，将 <code>instances</code> 示例替换为 Prometheus 中实际的 <code>instance</code> 标签值。</p>
            </el-form-item>
          </el-form>
          <details class="config-example" open>
            <summary>Kubernetes 扩展配置示例</summary>
            <pre><code>{{ kubernetesExampleJson }}</code></pre>
            <div class="config-example-actions">
              <p class="config-hint"><code>*</code> 匹配整个命名空间，<code>traefik.*</code> 匹配以 traefik 开头的 Pod。</p>
              <el-button size="small" :disabled="isBusy('prometheus')" @click="addKubernetesExample">添加到扩展配置</el-button>
            </div>
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

    <el-dialog v-model="prometheusHelpVisible" title="Prometheus 监控 Kubernetes 配置" width="760px" top="6vh" append-to-body>
      <div class="prometheus-help" tabindex="0" role="region" aria-label="Kubernetes 监控配置步骤">
        <section>
          <h2>1. 准备监控账号和令牌</h2>
          <p>在连接目标集群的机器上，用 <code>kubectl</code> 为专用 ServiceAccount 签发令牌。示例使用已有的 <code>monitoring</code> 命名空间，首次配置时创建账号。</p>
          <pre><code>kubectl -n monitoring create serviceaccount prometheus-cadvisor
(umask 077; kubectl -n monitoring create token prometheus-cadvisor --duration=1h &gt; token)</code></pre>
          <p>请集群管理员为账号授予 <code>nodes</code> 的 <code>list</code>、<code>watch</code> 和 API Server 代理采集所需的 <code>nodes/proxy</code> 的 <code>get</code> 权限。<code>nodes/proxy</code> 也允许访问其他 kubelet 接口。</p>
          <p>命令会把令牌原文写入单行文件。将令牌文件和 API Server 的 CA 证书放入 Prometheus 可读的目录，并在令牌到期前更新。容器部署时请挂载该目录，下面使用容器内的路径。</p>
        </section>
        <section>
          <h2>2. 配置 Prometheus 抓取 cAdvisor</h2>
          <p>在 <code>prometheus.yml</code> 的 <code>scrape_configs</code> 中添加以下任务。替换两处 API Server 地址、令牌和 CA 路径。两处认证配置分别用于发现节点和抓取指标。使用 <code>bearer_token_file</code> 的旧配置也应填写令牌的完整文件路径。</p>
          <pre><code>{{ prometheusScrapeExample }}</code></pre>
        </section>
        <section>
          <h2>3. 验证指标并填写项目配置</h2>
          <ol>
            <li>用 <code>promtool check config</code> 检查配置后重载 Prometheus。在 Prometheus 中查询 <code>up{job="kubernetes-cadvisor"}</code>，确认各节点为 <code>1</code>。</li>
            <li>查询 <code>container_cpu_usage_seconds_total{namespace="als9"}</code>，确认有带 <code>namespace</code> 和 <code>pod</code> 标签的指标。将命名空间替换为集群中实际监控的命名空间。</li>
            <li>在项目的 Prometheus 服务地址中填写平台后端可访问的 Prometheus 地址。将 <code>k8sPods</code> 示例添加到扩展配置，修改命名空间和 Pod 匹配规则，再测试连接并保存。仅监控 Pod 时可以移除 <code>instances</code>。</li>
          </ol>
        </section>
      </div>
    </el-dialog>
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
.prometheus-intro,
.config-example-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-xs) var(--space-base);
}
.prometheus-intro {
  margin-bottom: var(--space-lg);
}
.prometheus-intro .config-description {
  margin: 0;
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
.code-input + .config-hint {
  margin-top: var(--space-xs);
  line-height: 1.5;
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
.prometheus-help {
  max-height: 68vh;
  overflow-y: auto;
  line-height: 1.6;
}
.prometheus-help h2 {
  margin: var(--space-base) 0 var(--space-xs);
  color: var(--el-text-color-primary);
  font-size: 16px;
}
.prometheus-help section:first-child h2 {
  margin-top: 0;
}
.prometheus-help p,
.prometheus-help ol {
  margin: var(--space-xs) 0;
  overflow-wrap: anywhere;
}
.prometheus-help ol {
  padding-left: var(--space-lg);
}
.prometheus-help li + li {
  margin-top: var(--space-xs);
}
.config-example pre,
.prometheus-help pre {
  margin: var(--space-sm) 0;
  padding: var(--space-sm);
  overflow-x: auto;
  border-radius: var(--radius-input);
  background: var(--color-surface);
  font-size: 13px;
}
.prometheus-help pre {
  background: var(--color-canvas-soft);
}
.config-example pre code,
.prometheus-help pre code {
  padding: 0;
  background: transparent;
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
