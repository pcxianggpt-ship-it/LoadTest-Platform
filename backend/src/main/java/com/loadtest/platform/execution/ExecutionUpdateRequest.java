package com.loadtest.platform.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExecutionUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String executionName;

    @Size(max = 500)
    private String remark;
}
