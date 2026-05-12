package com.loadtest.platform.projectconfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JMeterServerRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private Integer sshPort;

    @NotBlank
    private String sshUsername;

    @NotBlank
    @Pattern(regexp = "password|private_key")
    private String sshAuthType;

    private String sshPasswordEncrypted;
    private String sshPrivateKeyEncrypted;

    @NotBlank
    private String jmeterHome;

    @NotBlank
    private String scriptDir;

    private String resultDir;

    @NotBlank
    private String logDir;
}
