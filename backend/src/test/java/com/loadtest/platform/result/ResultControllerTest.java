package com.loadtest.platform.result;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.metrics.InfluxMetricClient;
import com.loadtest.platform.metrics.MetricSample;
import com.loadtest.platform.metrics.PrometheusMetricClient;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ResultControllerTest {

    private static final Path DB_PATH = Path.of("target", "result-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestExecutionMapper testExecutionMapper;

    @MockBean(name = "defaultInfluxMetricClient")
    private InfluxMetricClient influxMetricClient;

    @MockBean(name = "defaultPrometheusMetricClient")
    private PrometheusMetricClient prometheusMetricClient;

    @Test
    void generatesSuccessResultFromSuccessExecution() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long executionId = createSuccessExecution(projectId);
        when(influxMetricClient.queryJMeterSummary(any(), any(), any()))
                .thenReturn(jmeterMetrics(BigDecimal.valueOf(800)));
        when(prometheusMetricClient.queryServerResourceSummary(any(), any(), any(), any()))
                .thenReturn(resourceMetrics(BigDecimal.valueOf(60)));

        mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"基准压测结果\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("基准压测结果"))
                .andExpect(jsonPath("$.data.metrics.length()").value(9))
                .andExpect(jsonPath("$.data.analysisJson").value(org.hamcrest.Matchers.containsString("overallStatus")));

        mockMvc.perform(get("/api/projects/{projectId}/results", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void rejectsResultGenerationFromNonSuccessExecution() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);

        mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无效结果\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void marksResultFailedWhenInfluxQueryFails() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long executionId = createSuccessExecution(projectId);
        when(influxMetricClient.queryJMeterSummary(any(), any(), any()))
                .thenThrow(new IllegalStateException("influx unavailable"));

        mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"失败结果\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.metrics.length()").value(0));
    }

    @Test
    void marksResultPartialSuccessWhenPrometheusQueryFails() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long executionId = createSuccessExecution(projectId);
        when(influxMetricClient.queryJMeterSummary(any(), any(), any()))
                .thenReturn(jmeterMetrics(BigDecimal.valueOf(800)));
        when(prometheusMetricClient.queryServerResourceSummary(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("prometheus unavailable"));

        mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"部分成功结果\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("partial_success"))
                .andExpect(jsonPath("$.data.summaryJson").value(org.hamcrest.Matchers.containsString("prometheus unavailable")))
                .andExpect(jsonPath("$.data.analysisJson").value(org.hamcrest.Matchers.containsString("Prometheus 指标采集失败")))
                .andExpect(jsonPath("$.data.metrics.length()").value(2));
    }

    @Test
    void analysisFlagsCriticalThresholds() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long executionId = createSuccessExecution(projectId);
        when(influxMetricClient.queryJMeterSummary(any(), any(), any()))
                .thenReturn(jmeterMetrics(BigDecimal.valueOf(3500)));
        when(prometheusMetricClient.queryServerResourceSummary(any(), any(), any(), any()))
                .thenReturn(resourceMetrics(BigDecimal.valueOf(95)));

        mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"风险结果\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisJson").value(org.hamcrest.Matchers.containsString("critical")))
                .andExpect(jsonPath("$.data.metrics[0].thresholdStatus").value("critical"));
    }

    @Test
    void deletesResult() throws Exception {
        Long projectId = createProjectWithDatasources();
        Long executionId = createSuccessExecution(projectId);
        when(influxMetricClient.queryJMeterSummary(any(), any(), any()))
                .thenReturn(jmeterMetrics(BigDecimal.valueOf(800)));
        when(prometheusMetricClient.queryServerResourceSummary(any(), any(), any(), any()))
                .thenReturn(resourceMetrics(BigDecimal.valueOf(60)));
        Long resultId = extractId(mockMvc.perform(post("/api/executions/{executionId}/results", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除结果\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(delete("/api/results/{resultId}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/results/{resultId}", resultId))
                .andExpect(status().isNotFound());
    }

    private Long createProjectWithDatasources() throws Exception {
        Long projectId = createProject();
        putDatasource(projectId, "influxdb", """
                {
                  "name": "jmeter-influx",
                  "baseUrl": "http://10.0.0.20:8086",
                  "databaseName": "jmeter",
                  "extraConfigJson": "{\\"measurement\\":\\"jmeter\\"}"
                }
                """);
        putDatasource(projectId, "prometheus", """
                {
                  "name": "resource-prometheus",
                  "baseUrl": "http://10.0.0.21:9090",
                  "extraConfigJson": "{\\"instances\\":[\\"10.0.0.11:9100\\"]}"
                }
                """);
        return projectId;
    }

    private Long createProject() throws Exception {
        String body = """
                {
                  "name": "订单系统压测",
                  "environmentName": "test"
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void putDatasource(Long projectId, String type, String body) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/datasources/{type}", projectId, type)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private Long createSuccessExecution(Long projectId) throws Exception {
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        String now = OffsetDateTime.now().toString();
        TestExecution execution = testExecutionMapper.selectById(executionId);
        execution.setStatus("success");
        execution.setStartedAt(now);
        execution.setEndedAt(now);
        execution.setDurationSeconds(600);
        execution.setUpdatedAt(now);
        testExecutionMapper.updateById(execution);
        return executionId;
    }

    private Long createTask(Long projectId) throws Exception {
        String body = """
                {
                  "name": "订单查询压测",
                  "step": {
                    "stepName": "订单查询",
                    "jmxFile": "order_query.jmx",
                    "threads": 100,
                    "durationSeconds": 600,
                    "rampUpSeconds": 60
                  }
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createManualExecution(Long taskId) throws Exception {
        return extractId(mockMvc.perform(post("/api/tasks/{taskId}/executions/manual", taskId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<MetricSample> jmeterMetrics(BigDecimal artP95) {
        return List.of(
                sample("influxdb", "jmeter", "ART", "all", "p95", artP95, "ms"),
                sample("influxdb", "jmeter", "TPS", "all", "avg", BigDecimal.valueOf(120), "req/s")
        );
    }

    private List<MetricSample> resourceMetrics(BigDecimal cpuMax) {
        return List.of(
                sample("prometheus", "cpu", "CPU usage", "10.0.0.11:9100", "max", cpuMax, "%"),
                sample("prometheus", "memory", "Memory usage", "10.0.0.11:9100", "max", BigDecimal.valueOf(70), "%"),
                sample("prometheus", "disk", "Disk usage", "10.0.0.11:9100", "max", BigDecimal.valueOf(50), "%"),
                sample("prometheus", "disk_io", "IO wait", "10.0.0.11:9100", "max", BigDecimal.valueOf(3), "%"),
                sample("prometheus", "network", "Network receive", "10.0.0.11:9100", "max", BigDecimal.valueOf(1000), "B/s"),
                sample("prometheus", "network", "Network transmit", "10.0.0.11:9100", "max", BigDecimal.valueOf(2000), "B/s"),
                sample("prometheus", "load", "Load 1m", "10.0.0.11:9100", "max", BigDecimal.valueOf(1.5), "")
        );
    }

    private MetricSample sample(
            String source,
            String category,
            String name,
            String target,
            String statType,
            BigDecimal value,
            String unit
    ) {
        return MetricSample.builder()
                .source(source)
                .metricCategory(category)
                .metricName(name)
                .targetName(target)
                .statType(statType)
                .value(value)
                .unit(unit)
                .build();
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
