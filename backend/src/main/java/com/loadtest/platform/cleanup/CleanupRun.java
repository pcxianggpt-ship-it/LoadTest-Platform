package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cleanup_runs")
public class CleanupRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Long cleanupPlanId;
    private Long businessDatabaseId;
    private String status;
    private String startedAt;
    private String endedAt;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
}
