package com.loadtest.platform.execution;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExecutionRequest {

    @NotBlank
    private String scheduledAt;
}
