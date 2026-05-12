package com.loadtest.platform.execution;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionResponse {

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
    private String errorMessage;
    private String createdBy;
    private String createdAt;
    private String updatedAt;

    public static ExecutionResponse from(TestExecution execution) {
        return ExecutionResponse.builder()
                .id(execution.getId())
                .projectId(execution.getProjectId())
                .taskId(execution.getTaskId())
                .executionName(execution.getExecutionName())
                .triggerType(execution.getTriggerType())
                .scheduledAt(execution.getScheduledAt())
                .status(execution.getStatus())
                .startedAt(execution.getStartedAt())
                .endedAt(execution.getEndedAt())
                .durationSeconds(execution.getDurationSeconds())
                .currentStepOrder(execution.getCurrentStepOrder())
                .errorMessage(execution.getErrorMessage())
                .createdBy(execution.getCreatedBy())
                .createdAt(execution.getCreatedAt())
                .updatedAt(execution.getUpdatedAt())
                .build();
    }
}
