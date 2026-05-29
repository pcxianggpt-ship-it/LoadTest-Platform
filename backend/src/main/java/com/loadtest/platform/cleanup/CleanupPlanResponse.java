package com.loadtest.platform.cleanup;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CleanupPlanResponse {

    private Long id;
    private Long projectId;
    private Long businessDatabaseId;
    private String name;
    private String description;
    private Boolean enabled;
    private String status;
    private String createdAt;
    private String updatedAt;
    private List<String> sqlStatements;

    public static CleanupPlanResponse from(CleanupPlan plan, List<CleanupPlanSql> sqls) {
        return CleanupPlanResponse.builder()
                .id(plan.getId())
                .projectId(plan.getProjectId())
                .businessDatabaseId(plan.getBusinessDatabaseId())
                .name(plan.getName())
                .description(plan.getDescription())
                .enabled(plan.getEnabled())
                .status(plan.getStatus())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .sqlStatements(sqls.stream().map(CleanupPlanSql::getSqlText).toList())
                .build();
    }
}
