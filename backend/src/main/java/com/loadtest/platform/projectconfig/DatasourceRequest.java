package com.loadtest.platform.projectconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DatasourceRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Pattern(regexp = "^https?://.+")
    private String baseUrl;

    private String databaseName;
    private String username;
    private String passwordEncrypted;
    private String tokenEncrypted;
    private String extraConfigJson;
}
