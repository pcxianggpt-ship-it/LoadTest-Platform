package com.loadtest.platform.task;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestTaskStepResponse {

    private Long id;
    private Long taskId;
    private Integer stepOrder;
    private String stepName;
    private String jmxFile;
    private Integer threads;
    private Integer durationSeconds;
    private Integer rampUpSeconds;
    private Boolean saveJtl;
    private String jmeterArgsJson;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;

    public static TestTaskStepResponse from(TestTaskStep step) {
        return TestTaskStepResponse.builder()
                .id(step.getId())
                .taskId(step.getTaskId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .jmxFile(step.getJmxFile())
                .threads(step.getThreads())
                .durationSeconds(step.getDurationSeconds())
                .rampUpSeconds(step.getRampUpSeconds())
                .saveJtl(step.getSaveJtl())
                .jmeterArgsJson(step.getJmeterArgsJson())
                .enabled(step.getEnabled())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();
    }
}
