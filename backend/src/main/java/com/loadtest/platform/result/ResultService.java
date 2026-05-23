package com.loadtest.platform.result;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.metrics.InfluxMetricClient;
import com.loadtest.platform.metrics.MetricSample;
import com.loadtest.platform.metrics.PrometheusMetricClient;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import com.loadtest.platform.projectconfig.ProjectDatasourceMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final TestExecutionMapper testExecutionMapper;
    private final TestResultMapper testResultMapper;
    private final TestResultMetricMapper testResultMetricMapper;
    private final ProjectDatasourceMapper projectDatasourceMapper;
    private final InfluxMetricClient influxMetricClient;
    private final PrometheusMetricClient prometheusMetricClient;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResultResponse generateResultFromExecution(Long executionId, String name) {
        TestExecution execution = getExecutionOrThrow(executionId);
        if (!"success".equals(execution.getStatus())) {
            throw new IllegalArgumentException("result can only be generated from success execution");
        }

        String now = OffsetDateTime.now().toString();
        TestResult result = new TestResult();
        result.setProjectId(execution.getProjectId());
        result.setExecutionId(execution.getId());
        result.setName(name);
        result.setTimeRangeStart(execution.getStartedAt());
        result.setTimeRangeEnd(execution.getEndedAt());
        result.setCreatedAt(now);
        result.setUpdatedAt(now);

        List<MetricSample> metrics = new ArrayList<>();
        boolean resourceMetricsIncomplete = false;
        try {
            log.info(
                    "Generating result from execution: executionId={}, projectId={}, startedAt={}, endedAt={}",
                    execution.getId(),
                    execution.getProjectId(),
                    execution.getStartedAt(),
                    execution.getEndedAt()
            );
            metrics.addAll(influxMetricClient.queryJMeterSummary(
                    datasource(execution.getProjectId(), "influxdb"),
                    execution.getStartedAt(),
                    execution.getEndedAt()
            ));
        } catch (Exception exception) {
            log.warn(
                    "Failed to collect JMeter metrics: executionId={}, projectId={}, error={}",
                    execution.getId(),
                    execution.getProjectId(),
                    exception.getMessage(),
                    exception
            );
            result.setStatus("failed");
            result.setSummaryJson(jsonString(new ResultSummary(0, exception.getMessage())));
            result.setAnalysisJson(jsonString(new AnalysisSummary(
                    "critical",
                    List.of("influx metrics unavailable"),
                    "JMeter 指标采集失败",
                    "请检查 InfluxDB 配置和 JMeter Backend Listener 写入结构"
            )));
            testResultMapper.insert(result);
            return ResultResponse.from(result, List.of());
        }

        try {
            ProjectDatasource prometheus = datasource(execution.getProjectId(), "prometheus");
            metrics.addAll(prometheusMetricClient.queryServerResourceSummary(
                    prometheus,
                    parseInstances(prometheus.getExtraConfigJson()),
                    execution.getStartedAt(),
                    execution.getEndedAt()
            ));
            result.setStatus("success");
        } catch (Exception exception) {
            resourceMetricsIncomplete = true;
            result.setStatus("partial_success");
        }

        AnalysisSummary analysis = analysisService.analyze(metrics, resourceMetricsIncomplete);
        result.setSummaryJson(jsonString(new ResultSummary(metrics.size(), null)));
        result.setAnalysisJson(jsonString(analysis));
        testResultMapper.insert(result);

        List<TestResultMetric> savedMetrics = new ArrayList<>();
        for (MetricSample sample : metrics) {
            TestResultMetric metric = toEntity(result.getId(), sample, now);
            testResultMetricMapper.insert(metric);
            savedMetrics.add(metric);
        }

        return ResultResponse.from(result, savedMetrics);
    }

    public List<ResultResponse> listResults(Long projectId) {
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<TestResult>()
                .eq(TestResult::getProjectId, projectId)
                .orderByDesc(TestResult::getId);
        return testResultMapper.selectList(wrapper).stream()
                .map(result -> ResultResponse.from(result, metrics(result.getId())))
                .toList();
    }

    public ResultResponse getResult(Long resultId) {
        TestResult result = testResultMapper.selectById(resultId);
        if (result == null) {
            throw new NotFoundException("result not found");
        }
        return ResultResponse.from(result, metrics(resultId));
    }

    private TestResultMetric toEntity(Long resultId, MetricSample sample, String now) {
        TestResultMetric metric = new TestResultMetric();
        metric.setResultId(resultId);
        metric.setSource(sample.getSource());
        metric.setMetricCategory(sample.getMetricCategory());
        metric.setMetricName(sample.getMetricName());
        metric.setTargetName(sample.getTargetName());
        metric.setStatType(sample.getStatType());
        metric.setValue(sample.getValue());
        metric.setUnit(sample.getUnit());
        metric.setThresholdStatus(analysisService.thresholdStatus(sample));
        metric.setThresholdValue(analysisService.thresholdValue(sample));
        metric.setExtraTagsJson(sample.getExtraTagsJson());
        metric.setCreatedAt(now);
        return metric;
    }

    private List<TestResultMetric> metrics(Long resultId) {
        LambdaQueryWrapper<TestResultMetric> wrapper = new LambdaQueryWrapper<TestResultMetric>()
                .eq(TestResultMetric::getResultId, resultId)
                .orderByAsc(TestResultMetric::getId);
        return testResultMetricMapper.selectList(wrapper);
    }

    private ProjectDatasource datasource(Long projectId, String type) {
        log.info("Looking up datasource: projectId={}, type={}", projectId, type);
        LambdaQueryWrapper<ProjectDatasource> wrapper = new LambdaQueryWrapper<ProjectDatasource>()
                .eq(ProjectDatasource::getProjectId, projectId)
                .eq(ProjectDatasource::getType, type)
                .last("limit 1");
        ProjectDatasource datasource = projectDatasourceMapper.selectOne(wrapper);
        if (datasource == null) {
            List<ProjectDatasource> projectDatasources = projectDatasourceMapper.selectList(
                    new LambdaQueryWrapper<ProjectDatasource>()
                            .eq(ProjectDatasource::getProjectId, projectId)
                            .orderByAsc(ProjectDatasource::getId)
            );
            log.warn(
                    "Datasource not found: projectId={}, type={}, availableDatasources={}",
                    projectId,
                    type,
                    projectDatasources.stream()
                            .map(item -> "id=%s,type=%s,name=%s,baseUrl=%s,database=%s,status=%s".formatted(
                                    item.getId(),
                                    item.getType(),
                                    item.getName(),
                                    item.getBaseUrl(),
                                    item.getDatabaseName(),
                                    item.getStatus()
                            ))
                            .toList()
            );
            throw new NotFoundException(type + " datasource not found");
        }
        log.info(
                "Datasource found: projectId={}, type={}, datasourceId={}, name={}, baseUrl={}, database={}, status={}, extraConfigJson={}",
                projectId,
                type,
                datasource.getId(),
                datasource.getName(),
                datasource.getBaseUrl(),
                datasource.getDatabaseName(),
                datasource.getStatus(),
                datasource.getExtraConfigJson()
        );
        return datasource;
    }

    private TestExecution getExecutionOrThrow(Long executionId) {
        TestExecution execution = testExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw new NotFoundException("execution not found");
        }
        return execution;
    }

    private List<String> parseInstances(String extraConfigJson) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            InstanceConfig config = objectMapper.readValue(extraConfigJson, InstanceConfig.class);
            return config.instances == null ? Collections.emptyList() : config.instances;
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize json", exception);
        }
    }

    private record ResultSummary(int metricCount, String errorMessage) {
    }

    private record InstanceConfig(List<String> instances) {
    }
}
