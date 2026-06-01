package com.loadtest.platform.execution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_executions")
public class TestExecution {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long taskId;
    private String executionName;
    private String triggerType;
    private String scheduledAt;
    private String status;
    private String startedAt;
    private String endedAt;
    private Integer durationSeconds;
    private Integer currentStepOrder;
    private String sshLog;
    private String errorMessage;
    private String remark;
    private String cleanupStatus;
    private String cleanupStartedAt;
    private String cleanupEndedAt;
    private String cleanupErrorMessage;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
