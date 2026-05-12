package com.loadtest.platform.projectconfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasourceResponse {

    private Long id;
    private Long projectId;
    private String type;
    private String name;
    private String baseUrl;
    private String databaseName;
    private String username;
    private String extraConfigJson;
    private String status;
    private String createdAt;
    private String updatedAt;

    public static DatasourceResponse from(ProjectDatasource datasource) {
        return DatasourceResponse.builder()
                .id(datasource.getId())
                .projectId(datasource.getProjectId())
                .type(datasource.getType())
                .name(datasource.getName())
                .baseUrl(datasource.getBaseUrl())
                .databaseName(datasource.getDatabaseName())
                .username(datasource.getUsername())
                .extraConfigJson(datasource.getExtraConfigJson())
                .status(datasource.getStatus())
                .createdAt(datasource.getCreatedAt())
                .updatedAt(datasource.getUpdatedAt())
                .build();
    }
}
