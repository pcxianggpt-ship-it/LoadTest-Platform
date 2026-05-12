package com.loadtest.platform.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectCreateRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String description;

    @NotBlank
    @Size(max = 120)
    private String environmentName;
}
