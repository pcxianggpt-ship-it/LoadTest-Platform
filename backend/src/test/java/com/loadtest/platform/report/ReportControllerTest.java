package com.loadtest.platform.report;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.project.Project;
import com.loadtest.platform.project.ProjectMapper;
import com.loadtest.platform.result.TestResult;
import com.loadtest.platform.result.TestResultImage;
import com.loadtest.platform.result.TestResultImageMapper;
import com.loadtest.platform.result.TestResultMapper;
import com.loadtest.platform.result.TestResultMetric;
import com.loadtest.platform.result.TestResultMetricMapper;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    private static final Path DB_PATH = Path.of("target", "report-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestResultMapper testResultMapper;

    @Autowired
    private TestResultMetricMapper testResultMetricMapper;

    @Autowired
    private TestResultImageMapper testResultImageMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private TestTaskMapper testTaskMapper;

    @Autowired
    private TestExecutionMapper testExecutionMapper;

    @Test
    void generatesReportFromSuccessResultWithMarkdownAndHtml() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addMetric(resultId, "jmeter", "TPS", "all", "avg", BigDecimal.valueOf(120), "req/s", "normal");
        addMetric(resultId, "cpu", "CPU usage", "10.0.0.11:9100", "max", BigDecimal.valueOf(65), "%", "normal");

        mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportType").value("single_result_text"))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.resultIdsJson").value("[" + resultId + "]"))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 1. 测试概述")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 9. 后续建议")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("<h2>1. 测试概述</h2>")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("<table>")));

        mockMvc.perform(get("/api/projects/{projectId}/reports", testResultMapper.selectById(resultId).getProjectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void generatesReadableAnalysisWithServerAndPodResourceSummary() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "TPS", "all", "avg", BigDecimal.valueOf(428.26), "req/s", "normal");
        addMetric(resultId, "jmeter", "TPS", "all", "max", BigDecimal.valueOf(458.4), "req/s", "normal");
        addMetric(resultId, "jmeter", "ART", "all", "avg", BigDecimal.valueOf(74.01), "ms", "normal");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(480.92), "ms", "normal");
        addMetric(resultId, "jmeter", "error_rate", "all", "avg", BigDecimal.valueOf(0.00081), "%", "normal");
        addMetric(resultId, "jmeter", "failed_requests", "all", "sum", BigDecimal.valueOf(3), "count", "normal");
        addMetric(resultId, "cpu", "CPU usage", "192.168.65.139:9100", "max", BigDecimal.valueOf(11.28), "%", "warning");
        addMetric(resultId, "memory", "Memory usage", "192.168.65.139:9100", "max", BigDecimal.valueOf(69.59), "%", "normal");
        addMetric(resultId, "disk", "Disk usage", "192.168.65.139:9100", "max", BigDecimal.valueOf(74.86), "%", "critical");
        addMetric(resultId, "network", "Network receive", "192.168.65.139:9100", "max", BigDecimal.valueOf(3.48), "MB/s", "normal");
        addMetric(resultId, "network", "Network transmit", "192.168.65.139:9100", "max", BigDecimal.valueOf(7.38), "MB/s", "normal");
        addMetric(resultId, "cpu", "CPU usage", "192.168.65.141:9105", "max", BigDecimal.valueOf(21.65), "%", "normal");
        addMetric(resultId, "memory", "Memory usage", "192.168.65.141:9105", "max", BigDecimal.valueOf(61.39), "%", "normal");
        addMetric(resultId, "k8s_pod_cpu", "Pod CPU usage", "default/order-api-1", "avg", BigDecimal.valueOf(0.72), "cores", "normal");
        addMetric(resultId, "k8s_pod_cpu", "Pod CPU usage", "default/order-api-2", "avg", BigDecimal.valueOf(0.41), "cores", "normal");
        addMetric(resultId, "k8s_pod_memory", "Pod memory usage", "default/order-api-1", "avg", BigDecimal.valueOf(512), "MiB", "normal");
        addMetric(resultId, "k8s_pod_memory", "Pod memory usage", "default/order-api-2", "avg", BigDecimal.valueOf(438), "MiB", "normal");
        addMetric(resultId, "k8s_pod_network", "Pod Network receive", "default/order-api-1", "avg", BigDecimal.valueOf(1.24), "MB/s", "normal");
        addMetric(resultId, "k8s_pod_network", "Pod Network transmit", "default/order-api-1", "avg", BigDecimal.valueOf(2.56), "MB/s", "normal");

        mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 8. 自动分析结论")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 6. Pod 资源表现")))
                .andExpect(jsonPath("$.data.contentMarkdown", not(containsString("阈值状态"))))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("### 总体判断")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("### 服务器资源使用概况")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>IP</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>CPU使用率</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("192.168.65.139:9100")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<td>192.168.65.139:9100</td>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<span class=\"metric-cell metric-cell-warning\">11.28%</span>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<span class=\"metric-cell metric-cell-critical\">74.86%</span>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("### Pod 资源使用概况")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>namespace</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>pod</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>cpu (avg)</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<th>memory (avg)</th>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<td>default</td><td>order-api-1</td>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<span class=\"metric-cell metric-cell-normal\">0.72 core</span>")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("<span class=\"metric-cell metric-cell-normal\">512.00 MB</span>")))
                .andExpect(jsonPath("$.data.contentMarkdown", not(containsString("Pod Network receive"))))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("458.40 req/s")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("3.00 count")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("Pod CPU")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("Pod 内存")));
    }

    @Test
    void rejectsFailedResult() throws Exception {
        Long resultId = createResult("failed");

        mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void generatesReportFromPartialSuccessResultAndWarnsAboutIncompleteResources() throws Exception {
        Long resultId = createResult("partial_success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(900), "ms", "normal");

        mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("资源指标不完整")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("资源指标不完整")));
    }

    @Test
    void embedsArchivedGrafanaImagesInReport() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addImage(resultId, "TPS 趋势", 7);

        mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("Grafana 图表归档")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("TPS 趋势")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("/api/result-images/")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("<figure class=\"grafana-image-figure\">")));
    }

    @Test
    void readsReportById() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");

        String response = mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long reportId = extractId(response);

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 4. 核心性能指标")));
    }

    @Test
    void deletesReport() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        Long reportId = extractId(mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(delete("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isNotFound());
    }

    private Long createResult(String status) {
        String now = OffsetDateTime.now().toString();
        Project project = new Project();
        project.setName("订单系统压测");
        project.setEnvironmentName("test");
        project.setStatus("active");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);

        TestTask task = new TestTask();
        task.setProjectId(project.getId());
        task.setName("订单查询压测");
        task.setDefaultSaveJtl(false);
        task.setStatus("active");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        testTaskMapper.insert(task);

        TestExecution execution = new TestExecution();
        execution.setProjectId(project.getId());
        execution.setTaskId(task.getId());
        execution.setExecutionName("订单查询压测执行");
        execution.setTriggerType("manual");
        execution.setStatus("success");
        execution.setStartedAt(now);
        execution.setEndedAt(now);
        execution.setDurationSeconds(600);
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        testExecutionMapper.insert(execution);

        TestResult result = new TestResult();
        result.setProjectId(project.getId());
        result.setExecutionId(execution.getId());
        result.setName("订单查询压测结果");
        result.setStatus(status);
        result.setTimeRangeStart(now);
        result.setTimeRangeEnd(now);
        result.setSummaryJson("{\"metricCount\":3}");
        result.setAnalysisJson("""
                {"overallStatus":"normal","riskItems":[],"summary":"整体表现稳定","suggestions":"继续观察核心链路"}
                """);
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        testResultMapper.insert(result);
        return result.getId();
    }

    private void addMetric(
            Long resultId,
            String category,
            String name,
            String target,
            String statType,
            BigDecimal value,
            String unit,
            String thresholdStatus
    ) {
        TestResultMetric metric = new TestResultMetric();
        metric.setResultId(resultId);
        metric.setSource("test");
        metric.setMetricCategory(category);
        metric.setMetricName(name);
        metric.setTargetName(target);
        metric.setStatType(statType);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setThresholdStatus(thresholdStatus);
        metric.setCreatedAt(OffsetDateTime.now().toString());
        testResultMetricMapper.insert(metric);
    }

    private void addImage(Long resultId, String title, Integer panelId) {
        TestResult result = testResultMapper.selectById(resultId);
        TestResultImage image = new TestResultImage();
        image.setResultId(resultId);
        image.setProjectId(result.getProjectId());
        image.setImageType("grafana_panel");
        image.setTitle(title);
        image.setDashboardUid("perf-main");
        image.setDashboardSlug("performance");
        image.setPanelId(panelId);
        image.setGrafanaUrl("http://grafana/render/d-solo/perf-main/performance?panelId=" + panelId);
        image.setFilePath("1/" + resultId + "/panel-" + panelId + ".png");
        image.setContentType("image/png");
        image.setFileSize(4L);
        image.setWidth(1200);
        image.setHeight(700);
        image.setCreatedAt(OffsetDateTime.now().toString());
        testResultImageMapper.insert(image);
    }

    private Long extractId(String responseBody) {
        String marker = "\"id\":";
        int start = responseBody.indexOf(marker) + marker.length();
        int end = responseBody.indexOf(",", start);
        return Long.parseLong(responseBody.substring(start, end));
    }
}
