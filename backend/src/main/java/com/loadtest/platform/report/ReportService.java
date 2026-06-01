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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
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
        List<TestResultMetric> jmeterMetrics = metrics.stream()
                .filter(metric -> "jmeter".equals(metric.getMetricCategory()))
                .toList();
        List<TestResultMetric> serverMetrics = metrics.stream()
                .filter(metric -> !"jmeter".equals(metric.getMetricCategory()))
                .filter(metric -> !isPodMetric(metric))
                .toList();
        List<TestResultMetric> podMetrics = metrics.stream()
                .filter(this::isPodMetric)
                .toList();

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
        appendMetricTable(markdown, jmeterMetrics);

        markdown.append("\n## 5. 服务器资源表现\n\n");
        appendMetricTable(markdown, serverMetrics);

        markdown.append("\n## 6. Pod 资源表现\n\n");
        appendMetricTable(markdown, podMetrics);

        markdown.append("\n## 7. 风险与异常\n\n");
        List<TestResultMetric> risks = metrics.stream()
                .filter(metric -> !"normal".equals(metric.getThresholdStatus()))
                .toList();
        if (risks.isEmpty()) {
            markdown.append("未发现超过默认阈值的指标。\n");
        } else {
            risks.forEach(metric -> markdown.append("- ")
                    .append(metricLabel(metric))
                    .append("：").append(formatMetricValue(metric))
                    .append("，状态 ").append(metric.getThresholdStatus()).append("\n"));
        }

        appendAnalysisConclusion(markdown, result, metrics, analysis);

        markdown.append("\n## 9. 后续建议\n\n")
                .append(value(analysis, "suggestions", "建议结合业务容量目标继续观察关键接口表现。")).append("\n");
        return markdown.toString();
    }

    private void appendAnalysisConclusion(
            StringBuilder markdown,
            TestResult result,
            List<TestResultMetric> metrics,
            Map<String, Object> analysis
    ) {
        List<TestResultMetric> jmeterMetrics = metrics.stream()
                .filter(metric -> "jmeter".equals(metric.getMetricCategory()))
                .toList();
        List<TestResultMetric> serverMetrics = metrics.stream()
                .filter(metric -> !"jmeter".equals(metric.getMetricCategory()))
                .filter(metric -> !isPodMetric(metric))
                .toList();
        List<TestResultMetric> podMetrics = metrics.stream()
                .filter(this::isPodMetric)
                .toList();
        List<TestResultMetric> risks = metrics.stream()
                .filter(metric -> !"normal".equals(metric.getThresholdStatus()))
                .toList();

        markdown.append("\n## 8. 自动分析结论\n\n");
        markdown.append("### 总体判断\n\n");
        if ("partial_success".equals(result.getStatus())) {
            markdown.append("本次压测结果为部分成功，资源指标存在采集不完整情况，以下结论仅作为当前已采集数据的参考。");
        } else if (risks.isEmpty()) {
            markdown.append("当前并发下系统整体表现正常，暂未发现明显性能瓶颈。本次压测结果可作为当前容量水平的基线参考。");
        } else {
            markdown.append("当前并发下系统存在需要关注的性能风险，建议结合关键证据和异常指标优先定位瓶颈。");
        }
        markdown.append("\n\n");

        markdown.append("### 关键证据\n\n");
        appendMetricEvidence(markdown, "吞吐表现", jmeterMetrics, "TPS", List.of("avg", "max"));
        appendMetricEvidence(markdown, "响应时间", jmeterMetrics, "ART", List.of("avg", "p95", "p99"));
        appendMetricEvidence(markdown, "错误情况", jmeterMetrics, "error_rate", List.of("avg"));
        appendMetricEvidence(markdown, "失败请求", jmeterMetrics, "failed_requests", List.of("sum"));
        markdown.append("- 服务器资源：").append(resourceStatusText(serverMetrics, "多台服务器 CPU、内存、磁盘 IO、网络和系统负载整体处于正常状态。")).append("\n");
        markdown.append("- Pod 资源：").append(podStatusText(podMetrics)).append("\n\n");

        markdown.append("### 服务器资源使用概况\n\n");
        appendServerResourceSummary(markdown, serverMetrics);

        markdown.append("\n### Pod 资源使用概况\n\n");
        appendPodResourceSummary(markdown, podMetrics);

        markdown.append("\n### 关注点\n\n");
        appendConcernSummary(markdown, serverMetrics, podMetrics, risks, analysis);
    }

    private void appendMetricEvidence(
            StringBuilder markdown,
            String label,
            List<TestResultMetric> metrics,
            String metricName,
            List<String> statTypes
    ) {
        List<String> values = statTypes.stream()
                .map(statType -> findMetric(metrics, "jmeter", metricName, statType))
                .flatMap(Optional::stream)
                .map(metric -> metric.getStatType() + " " + formatMetricValue(metric))
                .toList();
        if (values.isEmpty()) {
            markdown.append("- ").append(label).append("：暂无 ").append(metricName).append(" 指标。\n");
            return;
        }
        markdown.append("- ").append(label).append("：").append(String.join("，", values)).append("。\n");
    }

    private void appendServerResourceSummary(StringBuilder markdown, List<TestResultMetric> serverMetrics) {
        if (serverMetrics.isEmpty()) {
            markdown.append("暂无服务器资源指标数据。\n");
            return;
        }

        Optional<TestResultMetric> topCpu = maxMetric(serverMetrics, "cpu", "CPU usage");
        Optional<TestResultMetric> topMemory = maxMetric(serverMetrics, "memory", "Memory usage");
        Optional<TestResultMetric> topDisk = maxMetric(serverMetrics, "disk", "Disk usage");
        Optional<TestResultMetric> topIoWait = maxMetric(serverMetrics, "disk_io", "IO wait");
        Optional<TestResultMetric> topLoad = maxMetric(serverMetrics, "load", "System load");
        Optional<TestResultMetric> focus = List.of(topMemory, topDisk, topCpu, topIoWait, topLoad).stream()
                .flatMap(Optional::stream)
                .max(Comparator.comparing(this::resourceFocusScore));

        markdown.append("多台服务器整体资源使用较平稳，未出现单项资源明显打满的情况。");
        topCpu.ifPresent(metric -> markdown.append("CPU 峰值最高节点为 ")
                .append(metric.getTargetName()).append("，约 ").append(formatMetricValue(metric)).append("；"));
        topMemory.ifPresent(metric -> markdown.append("内存峰值最高节点为 ")
                .append(metric.getTargetName()).append("，约 ").append(formatMetricValue(metric)).append("；"));
        topDisk.ifPresent(metric -> markdown.append("磁盘使用率最高节点为 ")
                .append(metric.getTargetName()).append("，约 ").append(formatMetricValue(metric)).append("。"));
        markdown.append("\n\n");

        markdown.append("网络收发流量、系统负载和 IO wait 用于辅助判断资源侧是否存在瓶颈。");
        topIoWait.ifPresent(metric -> markdown.append("本次 IO wait 最高节点为 ")
                .append(metric.getTargetName()).append("，约 ").append(formatMetricValue(metric)).append("；"));
        topLoad.ifPresent(metric -> markdown.append("系统负载最高节点为 ")
                .append(metric.getTargetName()).append("，约 ").append(formatMetricValue(metric)).append("。"));
        if (serverMetrics.stream().anyMatch(metric -> !"normal".equals(metric.getThresholdStatus()))) {
            markdown.append("综合来看，本次压测存在需要优先核查的服务器资源项。");
        } else {
            markdown.append("综合来看，本次压测的服务器资源侧表现支持当前并发下系统运行稳定的判断。");
        }
        focus.ifPresent(metric -> markdown.append("后续提升并发时，建议重点关注 ")
                .append(metric.getTargetName()).append(" 的 ")
                .append(resourceName(metric)).append(" 是否持续上升。"));
        markdown.append("\n");
    }

    private void appendPodResourceSummary(StringBuilder markdown, List<TestResultMetric> podMetrics) {
        if (podMetrics.isEmpty()) {
            markdown.append("暂无 Pod CPU 或内存指标数据。\n");
            return;
        }

        Optional<TestResultMetric> topCpu = maxMetric(podMetrics, "k8s_pod_cpu", "Pod CPU usage");
        Optional<TestResultMetric> topMemory = maxMetric(podMetrics, "k8s_pod_memory", "Pod memory usage");

        markdown.append("本次采集到应用 Pod 的 CPU 和内存使用情况。");
        topCpu.ifPresent(metric -> markdown.append("CPU 用量最高的 Pod 为 ")
                .append(metric.getTargetName()).append("，平均约 ").append(formatMetricValue(metric)).append("；"));
        topMemory.ifPresent(metric -> markdown.append("内存用量最高的 Pod 为 ")
                .append(metric.getTargetName()).append("，平均约 ").append(formatMetricValue(metric)).append("。"));
        markdown.append("\n\n");

        markdown.append("需要关注 Pod 间资源使用是否均衡。");
        topCpu.ifPresent(metric -> markdown.append("若 ").append(metric.getTargetName())
                .append(" 的 Pod CPU 持续高于其他实例，应优先排查热点接口、线程池、数据库调用和下游依赖耗时；"));
        topMemory.ifPresent(metric -> markdown.append("若 ").append(metric.getTargetName())
                .append(" 的 Pod 内存持续上升，应关注缓存、对象堆积、日志缓冲、连接池和潜在内存泄漏。"));
        markdown.append("后续压测应持续比较多个 Pod 的 CPU 和内存差异，确认是否存在单 Pod 热点或扩容需求。\n");
    }

    private void appendConcernSummary(
            StringBuilder markdown,
            List<TestResultMetric> serverMetrics,
            List<TestResultMetric> podMetrics,
            List<TestResultMetric> risks,
            Map<String, Object> analysis
    ) {
        if (!risks.isEmpty()) {
            markdown.append("本次存在超过默认阈值的指标，建议优先处理风险项：")
                    .append(String.join("；", risks.stream().map(this::metricRiskText).toList()))
                    .append("。\n");
            return;
        }

        Optional<TestResultMetric> serverFocus = serverMetrics.stream()
                .filter(metric -> List.of("cpu", "memory", "disk", "disk_io", "load").contains(metric.getMetricCategory()))
                .max(Comparator.comparing(this::resourceFocusScore));
        Optional<TestResultMetric> podFocus = podMetrics.stream()
                .max(Comparator.comparing(TestResultMetric::getValue));

        if (serverFocus.isEmpty() && podFocus.isEmpty()) {
            markdown.append(value(analysis, "summary", "暂未发现需要特别关注的资源项。")).append("\n");
            return;
        }

        serverFocus.ifPresent(metric -> markdown.append(metric.getTargetName())
                .append(" 是本次服务器资源占用相对较高的节点，建议关注 ")
                .append(resourceName(metric)).append(" 的后续变化。"));
        podFocus.ifPresent(metric -> markdown.append("同时建议关注 ")
                .append(metric.getTargetName())
                .append(" 的 ").append(resourceName(metric))
                .append("，确认是否存在单 Pod 热点或资源倾斜。"));
        markdown.append("\n");
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
                    .append(formatMetricValue(metric)).append(" | ")
                    .append(metric.getThresholdStatus() == null ? "normal" : metric.getThresholdStatus()).append(" |\n");
        }
    }

    private Optional<TestResultMetric> findMetric(
            List<TestResultMetric> metrics,
            String category,
            String name,
            String statType
    ) {
        return metrics.stream()
                .filter(metric -> category.equals(metric.getMetricCategory()))
                .filter(metric -> name.equals(metric.getMetricName()))
                .filter(metric -> statType.equals(metric.getStatType()))
                .findFirst();
    }

    private Optional<TestResultMetric> maxMetric(List<TestResultMetric> metrics, String category, String name) {
        return metrics.stream()
                .filter(metric -> category.equals(metric.getMetricCategory()))
                .filter(metric -> name.equals(metric.getMetricName()))
                .filter(metric -> metric.getValue() != null)
                .max(Comparator.comparing(TestResultMetric::getValue));
    }

    private boolean isPodMetric(TestResultMetric metric) {
        return "k8s_pod_cpu".equals(metric.getMetricCategory())
                || "k8s_pod_memory".equals(metric.getMetricCategory())
                || "k8s_pod_network".equals(metric.getMetricCategory());
    }

    private String resourceStatusText(List<TestResultMetric> metrics, String normalText) {
        if (metrics.isEmpty()) {
            return "暂无服务器资源指标数据。";
        }
        boolean hasRisk = metrics.stream().anyMatch(metric -> !"normal".equals(metric.getThresholdStatus()));
        return hasRisk ? "存在需要关注的服务器资源指标，建议结合风险与异常章节优先排查。" : normalText;
    }

    private String podStatusText(List<TestResultMetric> metrics) {
        if (metrics.isEmpty()) {
            return "暂无 Pod CPU 或内存指标数据。";
        }
        boolean hasRisk = metrics.stream().anyMatch(metric -> !"normal".equals(metric.getThresholdStatus()));
        return hasRisk
                ? "存在需要关注的 Pod CPU 或内存指标，建议确认是否出现单 Pod 热点或资源倾斜。"
                : "应用 Pod 的 CPU 和内存使用整体处于可接受范围，暂未发现单个 Pod 明显资源打满或异常倾斜。";
    }

    private String metricRiskText(TestResultMetric metric) {
        return metricLabel(metric) + " " + metric.getTargetName() + " " + formatMetricValue(metric)
                + "，状态 " + metric.getThresholdStatus();
    }

    private BigDecimal resourceFocusScore(TestResultMetric metric) {
        if (metric.getValue() == null) {
            return BigDecimal.ZERO;
        }
        if ("disk".equals(metric.getMetricCategory()) || "memory".equals(metric.getMetricCategory())) {
            return metric.getValue();
        }
        if ("cpu".equals(metric.getMetricCategory()) || "disk_io".equals(metric.getMetricCategory())) {
            return metric.getValue().multiply(BigDecimal.valueOf(0.8));
        }
        return metric.getValue().multiply(BigDecimal.valueOf(0.5));
    }

    private String resourceName(TestResultMetric metric) {
        return switch (metric.getMetricCategory()) {
            case "cpu" -> "CPU";
            case "memory" -> "内存";
            case "disk" -> "磁盘使用率";
            case "disk_io" -> "IO wait";
            case "network" -> metric.getMetricName();
            case "load" -> "系统负载";
            case "k8s_pod_cpu" -> "Pod CPU";
            case "k8s_pod_memory" -> "Pod 内存";
            case "k8s_pod_network" -> metric.getMetricName();
            default -> metric.getMetricName();
        };
    }

    private String formatMetricValue(TestResultMetric metric) {
        String unit = metric.getUnit() == null || metric.getUnit().isBlank() ? "" : " " + metric.getUnit();
        return formatNumber(metric.getValue()) + unit;
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) {
            return "未知";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String metricLabel(TestResultMetric metric) {
        return metric.getMetricCategory() + " / " + metric.getMetricName();
    }

    private String renderHtml(String markdown) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();
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
