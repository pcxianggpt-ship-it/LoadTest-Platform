package com.loadtest.platform.cleanup;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusinessDatabaseResponse {

    private Long id;
    private Long projectId;
    private String name;
    private String databaseType;
    private String jdbcUrl;
    private String username;
    private String status;
    private String createdAt;
    private String updatedAt;

    public static BusinessDatabaseResponse from(BusinessDatabase database) {
        return BusinessDatabaseResponse.builder()
                .id(database.getId())
                .projectId(database.getProjectId())
                .name(database.getName())
                .databaseType(database.getDatabaseType())
                .jdbcUrl(database.getJdbcUrl())
                .username(database.getUsername())
                .status(database.getStatus())
                .createdAt(database.getCreatedAt())
                .updatedAt(database.getUpdatedAt())
                .build();
    }
}
