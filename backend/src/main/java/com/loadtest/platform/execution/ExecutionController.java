package com.loadtest.platform.execution;

import com.loadtest.platform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping("/api/tasks/{taskId}/executions/manual")
    public ApiResponse<ExecutionResponse> createManualExecution(@PathVariable Long taskId) {
        return ApiResponse.ok(executionService.createManualExecution(taskId));
    }

    @PostMapping("/api/tasks/{taskId}/executions/scheduled")
    public ApiResponse<ExecutionResponse> createScheduledExecution(
            @PathVariable Long taskId,
            @Valid @RequestBody ExecutionRequest request
    ) {
        return ApiResponse.ok(executionService.createScheduledExecution(taskId, request));
    }

    @GetMapping("/api/projects/{projectId}/executions")
    public ApiResponse<List<ExecutionResponse>> listExecutions(@PathVariable Long projectId) {
        return ApiResponse.ok(executionService.listExecutions(projectId));
    }

    @GetMapping("/api/executions/{executionId}")
    public ApiResponse<ExecutionResponse> getExecution(@PathVariable Long executionId) {
        return ApiResponse.ok(executionService.getExecution(executionId));
    }

    @PostMapping("/api/executions/{executionId}/cancel")
    public ApiResponse<ExecutionResponse> cancelExecution(@PathVariable Long executionId) {
        return ApiResponse.ok(executionService.cancelExecution(executionId));
    }

    @DeleteMapping("/api/executions/{executionId}")
    public ApiResponse<Void> deleteExecution(@PathVariable Long executionId) {
        executionService.deleteExecution(executionId);
        return ApiResponse.ok(null);
    }
}
