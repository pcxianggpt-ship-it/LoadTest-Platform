package com.loadtest.platform.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CleanupPlanRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String description;

    @NotNull
    private Long businessDatabaseId;

    private Boolean enabled = true;

    @NotEmpty
    private List<@NotBlank String> sqlStatements;
}
