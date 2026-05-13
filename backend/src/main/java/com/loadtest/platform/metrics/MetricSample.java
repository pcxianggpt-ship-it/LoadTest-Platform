package com.loadtest.platform.metrics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricSample {

    private String source;
    private String metricCategory;
    private String metricName;
    private String targetName;
    private String statType;
    private BigDecimal value;
    private String unit;
    private String extraTagsJson;
}
