package com.loadtest.platform.projectconfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JMeterServerResponse {

    private Long id;
    private Long projectId;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUsername;
    private String sshAuthType;
    private String jmeterHome;
    private String scriptDir;
    private String resultDir;
    private String logDir;
    private String status;
    private String createdAt;
    private String updatedAt;

    public static JMeterServerResponse from(JMeterServer server) {
        return JMeterServerResponse.builder()
                .id(server.getId())
                .projectId(server.getProjectId())
                .name(server.getName())
                .host(server.getHost())
                .sshPort(server.getSshPort())
                .sshUsername(server.getSshUsername())
                .sshAuthType(server.getSshAuthType())
                .jmeterHome(server.getJmeterHome())
                .scriptDir(server.getScriptDir())
                .resultDir(server.getResultDir())
                .logDir(server.getLogDir())
                .status(server.getStatus())
                .createdAt(server.getCreatedAt())
                .updatedAt(server.getUpdatedAt())
                .build();
    }
}
