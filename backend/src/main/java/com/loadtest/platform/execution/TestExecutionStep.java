package com.loadtest.platform.execution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_execution_steps")
public class TestExecutionStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Long taskStepId;
    private Integer stepOrder;
    private String jmxFile;
    private String status;
    private String startedAt;
    private String endedAt;
    private Integer durationSeconds;
    private String command;
    private Integer exitCode;
    private String sshLog;
    private String errorMessage;
    private String jtlPath;
    private String remoteRunDir;
    private String remotePid;
    private String createdAt;
    private String updatedAt;
}
