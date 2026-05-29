package com.loadtest.platform.cleanup;

import com.loadtest.platform.common.ApiResponse;
import com.loadtest.platform.execution.ExecutionResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CleanupController {

    private final CleanupService cleanupService;

    @PostMapping("/api/projects/{projectId}/business-databases")
    public ApiResponse<BusinessDatabaseResponse> createBusinessDatabase(
            @PathVariable Long projectId,
            @Valid @RequestBody BusinessDatabaseRequest request
    ) {
        return ApiResponse.ok(cleanupService.createBusinessDatabase(projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/business-databases")
    public ApiResponse<List<BusinessDatabaseResponse>> listBusinessDatabases(@PathVariable Long projectId) {
        return ApiResponse.ok(cleanupService.listBusinessDatabases(projectId));
    }

    @PutMapping("/api/projects/{projectId}/business-databases/{databaseId}")
    public ApiResponse<BusinessDatabaseResponse> updateBusinessDatabase(
            @PathVariable Long projectId,
            @PathVariable Long databaseId,
            @Valid @RequestBody BusinessDatabaseRequest request
    ) {
        return ApiResponse.ok(cleanupService.updateBusinessDatabase(projectId, databaseId, request));
    }

    @DeleteMapping("/api/projects/{projectId}/business-databases/{databaseId}")
    public ApiResponse<Void> deleteBusinessDatabase(@PathVariable Long projectId, @PathVariable Long databaseId) {
        cleanupService.deleteBusinessDatabase(projectId, databaseId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/projects/{projectId}/cleanup-plans")
    public ApiResponse<CleanupPlanResponse> createCleanupPlan(
            @PathVariable Long projectId,
            @Valid @RequestBody CleanupPlanRequest request
    ) {
        return ApiResponse.ok(cleanupService.createCleanupPlan(projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/cleanup-plans")
    public ApiResponse<List<CleanupPlanResponse>> listCleanupPlans(@PathVariable Long projectId) {
        return ApiResponse.ok(cleanupService.listCleanupPlans(projectId));
    }

    @PutMapping("/api/projects/{projectId}/cleanup-plans/{planId}")
    public ApiResponse<CleanupPlanResponse> updateCleanupPlan(
            @PathVariable Long projectId,
            @PathVariable Long planId,
            @Valid @RequestBody CleanupPlanRequest request
    ) {
        return ApiResponse.ok(cleanupService.updateCleanupPlan(projectId, planId, request));
    }

    @DeleteMapping("/api/projects/{projectId}/cleanup-plans/{planId}")
    public ApiResponse<Void> deleteCleanupPlan(@PathVariable Long projectId, @PathVariable Long planId) {
        cleanupService.deleteCleanupPlan(projectId, planId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/executions/{executionId}/cleanup/retry")
    public ApiResponse<ExecutionResponse> retryCleanup(@PathVariable Long executionId) {
        return ApiResponse.ok(ExecutionResponse.from(cleanupService.retryCleanup(executionId)));
    }
}
