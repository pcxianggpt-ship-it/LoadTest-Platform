package com.loadtest.platform.result;

import com.loadtest.platform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @PostMapping("/api/executions/{executionId}/results")
    public ApiResponse<ResultResponse> generateResult(
            @PathVariable Long executionId,
            @Valid @RequestBody ResultRequest request
    ) {
        return ApiResponse.ok(resultService.generateResultFromExecution(executionId, request.getName()));
    }

    @GetMapping("/api/projects/{projectId}/results")
    public ApiResponse<List<ResultResponse>> listResults(@PathVariable Long projectId) {
        return ApiResponse.ok(resultService.listResults(projectId));
    }

    @GetMapping("/api/results/{resultId}")
    public ApiResponse<ResultResponse> getResult(@PathVariable Long resultId) {
        return ApiResponse.ok(resultService.getResult(resultId));
    }
}
