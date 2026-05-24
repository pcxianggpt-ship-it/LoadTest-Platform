package com.loadtest.platform.result;

import com.loadtest.platform.metrics.MetricSample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    public AnalysisSummary analyze(List<MetricSample> metrics, boolean resourceMetricsIncomplete) {
        return analyze(metrics, resourceMetricsIncomplete, null);
    }

    public AnalysisSummary analyze(
            List<MetricSample> metrics,
            boolean resourceMetricsIncomplete,
            String resourceMetricsError
    ) {
        List<String> risks = new ArrayList<>();
        String overallStatus = "normal";
        for (MetricSample metric : metrics) {
            String thresholdStatus = thresholdStatus(metric);
            if ("critical".equals(thresholdStatus)) {
                overallStatus = "critical";
                risks.add(metric.getMetricName() + " critical");
            } else if ("warning".equals(thresholdStatus) && !"critical".equals(overallStatus)) {
                overallStatus = "warning";
                risks.add(metric.getMetricName() + " warning");
            }
        }
        if (resourceMetricsIncomplete) {
            if ("normal".equals(overallStatus)) {
                overallStatus = "warning";
            }
            risks.add(resourceMetricsError == null || resourceMetricsError.isBlank()
                    ? "Prometheus 指标采集不完整"
                    : resourceMetricsError);
        }
        String summary = "normal".equals(overallStatus)
                ? "当前测试结果未发现明显风险"
                : "当前测试结果存在需要关注的风险";
        String suggestions = "critical".equals(overallStatus)
                ? "建议优先排查响应时间、错误率和服务器资源瓶颈"
                : "建议结合业务日志继续观察";
        return new AnalysisSummary(overallStatus, risks, summary, suggestions);
    }

    public String thresholdStatus(MetricSample metric) {
        BigDecimal value = metric.getValue();
        if (value == null) {
            return "normal";
        }
        String category = metric.getMetricCategory();
        String name = metric.getMetricName();
        String statType = metric.getStatType();
        if ("jmeter".equals(category) && "ART".equals(name) && "p95".equals(statType)) {
            return compare(value, BigDecimal.valueOf(1000), BigDecimal.valueOf(3000));
        }
        if ("jmeter".equals(category) && "error_rate".equals(name)) {
            return compare(value, BigDecimal.valueOf(1), BigDecimal.valueOf(5));
        }
        if ("cpu".equals(category) && "max".equals(statType)) {
            return compare(value, BigDecimal.valueOf(80), BigDecimal.valueOf(90));
        }
        if ("memory".equals(category) && "max".equals(statType)) {
            return compare(value, BigDecimal.valueOf(80), BigDecimal.valueOf(90));
        }
        if ("disk_io".equals(category) && "IO wait".equals(name) && "max".equals(statType)) {
            return compare(value, BigDecimal.valueOf(20), BigDecimal.valueOf(40));
        }
        return "normal";
    }

    public BigDecimal thresholdValue(MetricSample metric) {
        String status = thresholdStatus(metric);
        if ("normal".equals(status)) {
            return null;
        }
        if ("jmeter".equals(metric.getMetricCategory()) && "ART".equals(metric.getMetricName())) {
            return "critical".equals(status) ? BigDecimal.valueOf(3000) : BigDecimal.valueOf(1000);
        }
        if ("jmeter".equals(metric.getMetricCategory()) && "error_rate".equals(metric.getMetricName())) {
            return "critical".equals(status) ? BigDecimal.valueOf(5) : BigDecimal.valueOf(1);
        }
        if ("cpu".equals(metric.getMetricCategory()) || "memory".equals(metric.getMetricCategory())) {
            return "critical".equals(status) ? BigDecimal.valueOf(90) : BigDecimal.valueOf(80);
        }
        if ("disk_io".equals(metric.getMetricCategory())) {
            return "critical".equals(status) ? BigDecimal.valueOf(40) : BigDecimal.valueOf(20);
        }
        return null;
    }

    private String compare(BigDecimal value, BigDecimal warning, BigDecimal critical) {
        if (value.compareTo(critical) > 0) {
            return "critical";
        }
        if (value.compareTo(warning) > 0) {
            return "warning";
        }
        return "normal";
    }
}
