package com.loadtest.platform.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadtest.platform.cleanup.DeletionService;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.result.TestResult;
import com.loadtest.platform.result.TestResultMapper;
import com.loadtest.platform.result.TestResultMetric;
import com.loadtest.platform.result.TestResultMetricMapper;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String REPORT_TYPE = "single_result_text";

    private final TestReportMapper testReportMapper;
    private final TestResultMapper testResultMapper;
    private final TestResultMetricMapper testResultMetricMapper;
    private final TestExecutionMapper testExecutionMapper;
    private final TestTaskMapper testTaskMapper;
    private final ObjectMapper objectMapper;
    private final DeletionService deletionService;

    public ReportResponse generateReport(Long resultId) {
        TestResult result = testResultMapper.selectById(resultId);
        if (result == null) {
            throw new NotFoundException("result not found");
        }
        if ("failed".equals(result.getStatus())) {
            throw new IllegalArgumentException("failed result cannot generate report");
        }

        List<TestResultMetric> metrics = metrics(resultId);
        String markdown = buildMarkdown(result, metrics);
        String html = renderHtml(markdown);
        String now = OffsetDateTime.now().toString();

        TestReport report = new TestReport();
        report.setProjectId(result.getProjectId());
        report.setTitle(result.getName() + "测试报告");
        report.setReportType(REPORT_TYPE);
        report.setStatus("success");
        report.setContentMarkdown(markdown);
        report.setContentHtml(html);
        report.setResultIdsJson(jsonString(List.of(resultId)));
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        testReportMapper.insert(report);
        return ReportResponse.from(report);
    }

    public List<ReportResponse> listReports(Long projectId) {
        LambdaQueryWrapper<TestReport> wrapper = new LambdaQueryWrapper<TestReport>()
                .eq(TestReport::getProjectId, projectId)
                .orderByDesc(TestReport::getCreatedAt);
        return testReportMapper.selectList(wrapper).stream()
                .map(ReportResponse::from)
                .toList();
    }

    public ReportResponse getReport(Long reportId) {
        TestReport report = testReportMapper.selectById(reportId);
        if (report == null) {
            throw new NotFoundException("report not found");
        }
        return ReportResponse.from(report);
    }

    public void deleteReport(Long reportId) {
        if (testReportMapper.selectById(reportId) == null) {
            throw new NotFoundException("report not found");
        }
        deletionService.deleteReport(reportId);
    }

    private String buildMarkdown(TestResult result, List<TestResultMetric> metrics) {
        TestExecution execution = testExecutionMapper.selectById(result.getExecutionId());
        TestTask task = execution == null ? null : testTaskMapper.selectById(execution.getTaskId());
        Map<String, Object> analysis = parseJson(result.getAnalysisJson());

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(result.getName()).append("测试报告\n\n");
        markdown.append("## 1. 测试概述\n\n")
                .append("- 测试结果：").append(result.getName()).append("\n")
                .append("- 结果状态：").append(result.getStatus()).append("\n")
                .append("- 时间范围：").append(result.getTimeRangeStart()).append(" 至 ").append(result.getTimeRangeEnd()).append("\n");
        if ("partial_success".equals(result.getStatus())) {
            markdown.append("- 注意：资源指标不完整，报告中的服务器资源表现仅供参考。\n");
        }

        markdown.append("\n## 2. 测试配置\n\n")
                .append("- 测试任务：").append(task == null ? "未知" : task.getName()).append("\n")
                .append("- 是否保存 JTL：").append(task != null && Boolean.TRUE.equals(task.getDefaultSaveJtl()) ? "是" : "否").append("\n");

        markdown.append("\n## 3. 执行信息\n\n")
                .append("- 执行名称：").append(execution == null ? "未知" : execution.getExecutionName()).append("\n")
                .append("- 触发方式：").append(execution == null ? "未知" : execution.getTriggerType()).append("\n")
                .append("- 执行状态：").append(execution == null ? "未知" : execution.getStatus()).append("\n")
                .append("- 持续时间：").append(execution == null || execution.getDurationSeconds() == null ? "未知" : execution.getDurationSeconds() + " 秒").append("\n");

        markdown.append("\n## 4. 核心性能指标\n\n");
        appendMetricTable(markdown, metrics.stream()
                .filter(metric -> "jmeter".equals(metric.getMetricCategory()))
                .toList());

        markdown.append("\n## 5. 服务器资源表现\n\n");
        appendMetricTable(markdown, metrics.stream()
                .filter(metric -> !"jmeter".equals(metric.getMetricCategory()))
                .toList());

        markdown.append("\n## 6. 风险与异常\n\n");
        List<TestResultMetric> risks = metrics.stream()
                .filter(metric -> !"normal".equals(metric.getThresholdStatus()))
                .toList();
        if (risks.isEmpty()) {
            markdown.append("未发现超过默认阈值的指标。\n");
        } else {
            risks.forEach(metric -> markdown.append("- ")
                    .append(metricLabel(metric))
                    .append("：").append(metric.getValue()).append(metric.getUnit() == null ? "" : metric.getUnit())
                    .append("，状态 ").append(metric.getThresholdStatus()).append("\n"));
        }

        markdown.append("\n## 7. 自动分析结论\n\n")
                .append("- 整体状态：").append(value(analysis, "overallStatus", "未知")).append("\n")
                .append("- 结论：").append(value(analysis, "summary", "暂无自动分析结论")).append("\n");

        markdown.append("\n## 8. 后续建议\n\n")
                .append(value(analysis, "suggestions", "建议结合业务容量目标继续观察关键接口表现。")).append("\n");
        return markdown.toString();
    }

    private void appendMetricTable(StringBuilder markdown, List<TestResultMetric> metrics) {
        if (metrics.isEmpty()) {
            markdown.append("暂无指标数据。\n");
            return;
        }
        markdown.append("| 指标 | 对象 | 统计 | 数值 | 阈值状态 |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (TestResultMetric metric : metrics) {
            markdown.append("| ")
                    .append(metricLabel(metric)).append(" | ")
                    .append(metric.getTargetName()).append(" | ")
                    .append(metric.getStatType()).append(" | ")
                    .append(metric.getValue()).append(metric.getUnit() == null ? "" : " " + metric.getUnit()).append(" | ")
                    .append(metric.getThresholdStatus() == null ? "normal" : metric.getThresholdStatus()).append(" |\n");
        }
    }

    private String metricLabel(TestResultMetric metric) {
        return metric.getMetricCategory() + " / " + metric.getMetricName();
    }

    private String renderHtml(String markdown) {
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    private List<TestResultMetric> metrics(Long resultId) {
        LambdaQueryWrapper<TestResultMetric> wrapper = new LambdaQueryWrapper<TestResultMetric>()
                .eq(TestResultMetric::getResultId, resultId)
                .orderByAsc(TestResultMetric::getId);
        return testResultMetricMapper.selectList(wrapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String value(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize report json", exception);
        }
    }
}
