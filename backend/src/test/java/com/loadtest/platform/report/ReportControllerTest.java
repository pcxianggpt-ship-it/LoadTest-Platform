package com.loadtest.platform.report;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.grafana.GrafanaImageClient;
import com.loadtest.platform.grafana.GrafanaImageData;
import com.loadtest.platform.grafana.GrafanaRenderRequest;
import com.loadtest.platform.project.Project;
import com.loadtest.platform.project.ProjectMapper;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import com.loadtest.platform.projectconfig.ProjectDatasourceMapper;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "loadtest.report.scheduler.enabled=false")
@AutoConfigureMockMvc
class ReportControllerTest {

    private static final Path DB_PATH = Path.of("target", "report-controller-test.db");
    private static final Path IMAGE_DIR = Path.of("target", "report-controller-images");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
        registry.add("loadtest.grafana.image-dir", () -> IMAGE_DIR.toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestResultMapper testResultMapper;

    @Autowired
    private TestResultMetricMapper testResultMetricMapper;

    @Autowired
    private TestResultImageMapper testResultImageMapper;

    @Autowired
    private TestReportMapper testReportMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private TestTaskMapper testTaskMapper;

    @Autowired
    private TestExecutionMapper testExecutionMapper;

    @Autowired
    private ProjectDatasourceMapper projectDatasourceMapper;

    @MockBean(name = "defaultGrafanaImageClient")
    private GrafanaImageClient grafanaImageClient;

    @Test
    void doesNotRegisterReportSchedulerWhenDisabled() {
        assertThat(applicationContext.getBeansOfType(ReportScheduler.class)).isEmpty();
    }

    @Test
    void createsPendingReportAndCompletesItInBackground() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addMetric(resultId, "jmeter", "TPS", "all", "avg", BigDecimal.valueOf(120), "req/s", "normal");
        addMetric(resultId, "cpu", "CPU usage", "10.0.0.11:9100", "max", BigDecimal.valueOf(65), "%", "normal");

        Long reportId = extractId(mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportType").value("single_result_text"))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.resultIdsJson").value("[" + resultId + "]"))
                .andExpect(jsonPath("$.data.contentMarkdown").doesNotExist())
                .andExpect(jsonPath("$.data.contentHtml").doesNotExist())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString());

        reportService.runOnePendingReport();

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.contentMarkdown").isNotEmpty())
                .andExpect(jsonPath("$.data.contentHtml").isNotEmpty())
                .andExpect(jsonPath("$.data.finishedAt").exists());

        TestReport updated = testReportMapper.selectById(reportId);
        assertThat(updated.getStartedAt()).isNotBlank();
        assertThat(updated.getFinishedAt()).isNotBlank();

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

        Long reportId = createAndRunReport(resultId);

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
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

        Long reportId = createAndRunReport(resultId);

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("资源指标不完整")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("资源指标不完整")));
    }

    @Test
    void embedsArchivedGrafanaImagesInReport() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addImage(resultId, "TPS 趋势", 7);

        Long reportId = createAndRunReport(resultId);

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("Grafana 图表归档")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("TPS 趋势")))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("/api/result-images/")))
                .andExpect(jsonPath("$.data.contentHtml", containsString("<figure class=\"grafana-image-figure\">")));
    }

    @Test
    void exportsConfiguredGrafanaImagesBeforeCompletingReport() throws Exception {
        Long resultId = createResult("success");
        TestResult result = testResultMapper.selectById(resultId);
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addGrafanaDatasource(result.getProjectId(), """
                {
                  "orgId": 1,
                  "theme": "light",
                  "width": 1200,
                  "height": 700,
                  "panels": [
                    {
                      "title": "JMeter 总览",
                      "dashboardUid": "jmeter",
                      "dashboardSlug": "jmeter",
                      "panelId": 1,
                      "imageType": "jmeter"
                    },
                    {
                      "title": "Node Exporter CPU",
                      "dashboardUid": "node",
                      "dashboardSlug": "node-exporter",
                      "panelId": 2,
                      "imageType": "node_exporter"
                    }
                  ]
                }
                """);
        when(grafanaImageClient.renderPanel(any(), any()))
                .thenAnswer(invocation -> {
                    GrafanaRenderRequest request = invocation.getArgument(1);
                    return new GrafanaImageData(new byte[] {1, 2, 3, request.panelId().byteValue()}, "image/png",
                            "http://grafana/render/d-solo/" + request.dashboardUid() + "/"
                                    + request.dashboardSlug() + "?panelId=" + request.panelId());
                });

        Long reportId = createAndRunReport(resultId);

        List<TestResultImage> images = testResultImageMapper.selectList(
                new LambdaQueryWrapper<TestResultImage>()
                        .eq(TestResultImage::getResultId, resultId)
                        .orderByAsc(TestResultImage::getId));
        assertThat(images).hasSize(2);
        assertThat(images).extracting(TestResultImage::getImageType)
                .containsExactly("jmeter", "node_exporter");
        assertThat(images).extracting(TestResultImage::getTitle)
                .containsExactly("JMeter 总览", "Node Exporter CPU");

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.contentHtml", containsString("/api/result-images/")));

        ArgumentCaptor<GrafanaRenderRequest> requestCaptor = ArgumentCaptor.forClass(GrafanaRenderRequest.class);
        verify(grafanaImageClient, times(2)).renderPanel(any(), requestCaptor.capture());
        String expectedFrom = String.valueOf(OffsetDateTime.parse(result.getTimeRangeStart()).toInstant().toEpochMilli());
        String expectedTo = String.valueOf(OffsetDateTime.parse(result.getTimeRangeEnd()).toInstant().toEpochMilli());
        assertThat(requestCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.from()).isEqualTo(expectedFrom);
            assertThat(request.to()).isEqualTo(expectedTo);
        });
    }

    @Test
    void keepsManualGrafanaPanelImageWhenAutoPanelUsesDefaultImageType() throws Exception {
        Long resultId = createResult("success");
        TestResult result = testResultMapper.selectById(resultId);
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addImage(resultId, "手动导出图", 7);
        TestResultImage manualImage = testResultImageMapper.selectOne(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .eq(TestResultImage::getTitle, "手动导出图"));
        addGrafanaDatasource(result.getProjectId(), """
                {
                  "dashboardUid": "perf-main",
                  "panels": [
                    {
                      "title": "自动导出图",
                      "panelId": 8
                    }
                  ]
                }
                """);
        when(grafanaImageClient.renderPanel(any(), any()))
                .thenReturn(new GrafanaImageData(new byte[] {1, 2, 3, 4}, "image/png",
                        "http://grafana/render/d-solo/perf-main/_?panelId=8"));

        createAndRunReport(resultId);

        List<TestResultImage> images = testResultImageMapper.selectList(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .orderByAsc(TestResultImage::getId));
        assertThat(images).hasSize(2);
        assertThat(images).extracting(TestResultImage::getId).contains(manualImage.getId());
        assertThat(images).extracting(TestResultImage::getTitle).contains("手动导出图", "自动导出图");
        assertThat(testResultImageMapper.selectById(manualImage.getId())).isNotNull();
    }

    @Test
    void generatingReportTwiceKeepsPreviousReportImageRecords() throws Exception {
        Long resultId = createResult("success");
        TestResult result = testResultMapper.selectById(resultId);
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        addGrafanaDatasource(result.getProjectId(), """
                {
                  "dashboardUid": "perf-main",
                  "panels": [
                    {
                      "title": "自动导出图",
                      "panelId": 8
                    }
                  ]
                }
                """);
        when(grafanaImageClient.renderPanel(any(), any()))
                .thenReturn(new GrafanaImageData(new byte[] {1, 2, 3, 4}, "image/png",
                        "http://grafana/render/d-solo/perf-main/_?panelId=8"));

        createAndRunReport(resultId);
        TestResultImage firstImage = testResultImageMapper.selectOne(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .eq(TestResultImage::getTitle, "自动导出图"));
        createAndRunReport(resultId);

        assertThat(testResultImageMapper.selectById(firstImage.getId())).isNotNull();
        List<TestResultImage> images = testResultImageMapper.selectList(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .orderByAsc(TestResultImage::getId));
        assertThat(images).hasSize(2);
        assertThat(images).extracting(TestResultImage::getId).contains(firstImage.getId());
    }

    @Test
    void rejectsInvalidConfiguredGrafanaPanelValues() {
        assertInvalidGrafanaConfig("""
                {
                  "dashboardUid": "perf-main",
                  "panels": [
                    {
                      "title": "非法面板",
                      "panelId": 0,
                      "width": 0,
                      "height": -1
                    }
                  ]
                }
                """, "grafana panelId must be positive");
    }

    @Test
    void rejectsConfiguredGrafanaPanelNonPositiveSize() {
        assertInvalidGrafanaConfig("""
                {
                  "dashboardUid": "perf-main",
                  "panels": [
                    {
                      "title": "非法宽度",
                      "panelId": 1,
                      "width": 0,
                      "height": 700
                    }
                  ]
                }
                """, "grafana width must be positive");
    }

    @Test
    void rejectsConfiguredGrafanaPanelNonPositiveDefaultSize() {
        assertInvalidGrafanaConfig("""
                {
                  "dashboardUid": "perf-main",
                  "width": 1200,
                  "height": 0,
                  "panels": [
                    {
                      "title": "非法默认高度",
                      "panelId": 1
                    }
                  ]
                }
                """, "grafana height must be positive");
    }

    @Test
    void readsReportById() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");

        Long reportId = createAndRunReport(resultId);

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.contentMarkdown", containsString("## 4. 核心性能指标")));
    }

    @Test
    void deletesReport() throws Exception {
        Long resultId = createResult("success");
        addMetric(resultId, "jmeter", "ART", "all", "p95", BigDecimal.valueOf(850), "ms", "normal");
        Long reportId = createAndRunReport(resultId);

        mockMvc.perform(delete("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isNotFound());
    }

    @Test
    void marksPendingReportFailedWhenResultIdsJsonIsInvalid() {
        String now = OffsetDateTime.now().toString();
        Long resultId = createResult("success");
        TestResult result = testResultMapper.selectById(resultId);
        TestReport report = new TestReport();
        report.setProjectId(result.getProjectId());
        report.setTitle("invalid");
        report.setReportType("single_result_text");
        report.setStatus("pending");
        report.setResultIdsJson("invalid");
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        testReportMapper.insert(report);

        reportService.runOnePendingReport();

        TestReport updated = testReportMapper.selectById(report.getId());
        assertThat(updated.getStatus()).isEqualTo("failed");
        assertThat(updated.getErrorMessage()).isNotBlank();
        assertThat(updated.getFinishedAt()).isNotBlank();
    }

    @Test
    void skipsPendingReportWhenAnotherReportIsRunning() {
        Long runningResultId = createResult("success");
        Long pendingResultId = createResult("success");
        TestReport runningReport = createPendingReport(runningResultId, "running", OffsetDateTime.now().toString());
        runningReport.setStatus("running");
        runningReport.setStartedAt(OffsetDateTime.now().toString());
        testReportMapper.updateById(runningReport);
        TestReport pendingReport = createPendingReport(pendingResultId, "pending", OffsetDateTime.now().toString());

        reportService.runOnePendingReport();

        TestReport updatedPending = testReportMapper.selectById(pendingReport.getId());
        assertThat(updatedPending.getStatus()).isEqualTo("pending");
        assertThat(updatedPending.getStartedAt()).isNull();
        assertThat(updatedPending.getFinishedAt()).isNull();

        testReportMapper.deleteById(runningReport.getId());
        testReportMapper.deleteById(pendingReport.getId());
    }

    @Test
    void marksExpiredRunningReportFailedAndProcessesPendingReport() {
        Long runningResultId = createResult("success");
        Long pendingResultId = createResult("success");
        String now = OffsetDateTime.now().toString();
        TestReport runningReport = createPendingReport(runningResultId, "running", now);
        runningReport.setStatus("running");
        runningReport.setStartedAt(OffsetDateTime.now().minusMinutes(31).toString());
        testReportMapper.updateById(runningReport);
        TestReport pendingReport = createPendingReport(pendingResultId, "pending", now);

        reportService.runOnePendingReport();

        TestReport updatedRunning = testReportMapper.selectById(runningReport.getId());
        TestReport updatedPending = testReportMapper.selectById(pendingReport.getId());
        assertThat(updatedRunning.getStatus()).isEqualTo("failed");
        assertThat(updatedRunning.getErrorMessage()).isEqualTo("报告生成超时，请重新生成");
        assertThat(updatedRunning.getFinishedAt()).isNotBlank();
        assertThat(updatedPending.getStatus()).isEqualTo("success");
        assertThat(updatedPending.getStartedAt()).isNotBlank();
        assertThat(updatedPending.getFinishedAt()).isNotBlank();
    }

    @Test
    void treatsRunningReportWithInvalidStartedAtAsExpired() {
        Long runningResultId = createResult("success");
        Long pendingResultId = createResult("success");
        String now = OffsetDateTime.now().toString();
        TestReport runningReport = createPendingReport(runningResultId, "running", now);
        runningReport.setStatus("running");
        runningReport.setStartedAt("invalid");
        testReportMapper.updateById(runningReport);
        TestReport pendingReport = createPendingReport(pendingResultId, "pending", now);

        reportService.runOnePendingReport();

        TestReport updatedRunning = testReportMapper.selectById(runningReport.getId());
        TestReport updatedPending = testReportMapper.selectById(pendingReport.getId());
        assertThat(updatedRunning.getStatus()).isEqualTo("failed");
        assertThat(updatedRunning.getFinishedAt()).isNotBlank();
        assertThat(updatedPending.getStatus()).isEqualTo("success");
    }

    @Test
    void processesEarliestPendingReportCreatedAtFirst() {
        Long firstResultId = createResult("success");
        Long secondResultId = createResult("success");
        TestReport firstReport = createPendingReport(firstResultId, "first", OffsetDateTime.now().plusMinutes(1).toString());
        TestReport secondReport = createPendingReport(secondResultId, "second", OffsetDateTime.now().minusMinutes(1).toString());

        reportService.runOnePendingReport();

        TestReport updatedFirst = testReportMapper.selectById(firstReport.getId());
        TestReport updatedSecond = testReportMapper.selectById(secondReport.getId());
        assertThat(updatedFirst.getStatus()).isEqualTo("pending");
        assertThat(updatedFirst.getStartedAt()).isNull();
        assertThat(updatedFirst.getFinishedAt()).isNull();
        assertThat(updatedSecond.getStatus()).isEqualTo("success");
        assertThat(updatedSecond.getStartedAt()).isNotBlank();
        assertThat(updatedSecond.getFinishedAt()).isNotBlank();

        testReportMapper.deleteById(firstReport.getId());
    }

    @Test
    void processesPendingReportWithSameCreatedAtByIdAsc() {
        Long firstResultId = createResult("success");
        Long secondResultId = createResult("success");
        String createdAt = OffsetDateTime.now().toString();
        TestReport firstReport = createPendingReport(firstResultId, "first", createdAt);
        TestReport secondReport = createPendingReport(secondResultId, "second", createdAt);

        reportService.runOnePendingReport();

        TestReport updatedFirst = testReportMapper.selectById(firstReport.getId());
        TestReport updatedSecond = testReportMapper.selectById(secondReport.getId());
        assertThat(firstReport.getId()).isLessThan(secondReport.getId());
        assertThat(updatedFirst.getStatus()).isEqualTo("success");
        assertThat(updatedFirst.getStartedAt()).isNotBlank();
        assertThat(updatedFirst.getFinishedAt()).isNotBlank();
        assertThat(updatedSecond.getStatus()).isEqualTo("pending");
        assertThat(updatedSecond.getStartedAt()).isNull();
        assertThat(updatedSecond.getFinishedAt()).isNull();

        testReportMapper.deleteById(secondReport.getId());
    }

    private Long createAndRunReport(Long resultId) throws Exception {
        Long reportId = extractId(mockMvc.perform(post("/api/results/{resultId}/reports", resultId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        reportService.runOnePendingReport();
        return reportId;
    }

    private TestReport createPendingReport(Long resultId, String title, String createdAt) {
        TestResult result = testResultMapper.selectById(resultId);
        TestReport report = new TestReport();
        report.setProjectId(result.getProjectId());
        report.setTitle(title);
        report.setReportType("single_result_text");
        report.setStatus("pending");
        report.setResultIdsJson("[" + resultId + "]");
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        testReportMapper.insert(report);
        return report;
    }

    private void assertInvalidGrafanaConfig(String extraConfigJson, String errorMessage) {
        Long resultId = createResult("success");
        TestResult result = testResultMapper.selectById(resultId);
        addGrafanaDatasource(result.getProjectId(), extraConfigJson);
        TestReport report = createPendingReport(resultId, "invalid panel", OffsetDateTime.now().toString());

        reportService.runOnePendingReport();

        TestReport updated = testReportMapper.selectById(report.getId());
        assertThat(updated.getStatus()).isEqualTo("failed");
        assertThat(updated.getErrorMessage()).contains(errorMessage);
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

    private void addGrafanaDatasource(Long projectId, String extraConfigJson) {
        String now = OffsetDateTime.now().toString();
        ProjectDatasource datasource = new ProjectDatasource();
        datasource.setProjectId(projectId);
        datasource.setType("grafana");
        datasource.setName("main-grafana");
        datasource.setBaseUrl("http://grafana");
        datasource.setTokenEncrypted("token");
        datasource.setExtraConfigJson(extraConfigJson);
        datasource.setStatus("active");
        datasource.setCreatedAt(now);
        datasource.setUpdatedAt(now);
        projectDatasourceMapper.insert(datasource);
    }

    private Long extractId(String responseBody) {
        String marker = "\"id\":";
        int start = responseBody.indexOf(marker) + marker.length();
        int end = responseBody.indexOf(",", start);
        return Long.parseLong(responseBody.substring(start, end));
    }
}
