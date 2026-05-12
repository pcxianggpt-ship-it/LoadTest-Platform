package com.loadtest.platform.result;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_results")
public class TestResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long executionId;
    private String name;
    private String status;
    private String timeRangeStart;
    private String timeRangeEnd;
    private String summaryJson;
    private String analysisJson;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
