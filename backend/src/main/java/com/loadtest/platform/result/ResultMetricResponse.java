package com.loadtest.platform.result;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultMetricResponse {

    private Long id;
    private Long resultId;
    private String source;
    private String metricCategory;
    private String metricName;
    private String targetName;
    private String statType;
    private BigDecimal value;
    private String unit;
    private BigDecimal thresholdValue;
    private String thresholdStatus;
    private String extraTagsJson;
    private String createdAt;

    public static ResultMetricResponse from(TestResultMetric metric) {
        return ResultMetricResponse.builder()
                .id(metric.getId())
                .resultId(metric.getResultId())
                .source(metric.getSource())
                .metricCategory(metric.getMetricCategory())
                .metricName(metric.getMetricName())
                .targetName(metric.getTargetName())
                .statType(metric.getStatType())
                .value(metric.getValue())
                .unit(metric.getUnit())
                .thresholdValue(metric.getThresholdValue())
                .thresholdStatus(metric.getThresholdStatus())
                .extraTagsJson(metric.getExtraTagsJson())
                .createdAt(metric.getCreatedAt())
                .build();
    }
}
