package com.loadtest.platform.task;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestTaskResponse {

    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private Boolean defaultSaveJtl;
    private String status;
    private String createdAt;
    private String updatedAt;
    private List<TestTaskStepResponse> steps;

    public static TestTaskResponse from(TestTask task, List<TestTaskStep> steps) {
        return TestTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .name(task.getName())
                .description(task.getDescription())
                .defaultSaveJtl(task.getDefaultSaveJtl())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .steps(steps.stream().map(TestTaskStepResponse::from).toList())
                .build();
    }
}
