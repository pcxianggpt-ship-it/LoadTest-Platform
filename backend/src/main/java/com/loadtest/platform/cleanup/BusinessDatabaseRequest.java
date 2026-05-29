package com.loadtest.platform.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessDatabaseRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Pattern(regexp = "mysql|oracle")
    private String databaseType;

    @NotBlank
    private String jdbcUrl;

    private String username;
    private String passwordEncrypted;
    private String status = "active";
}
