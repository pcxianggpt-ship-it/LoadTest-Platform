package com.loadtest.platform.result;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("test_result_metrics")
public class TestResultMetric {

    @TableId(type = IdType.AUTO)
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
}
