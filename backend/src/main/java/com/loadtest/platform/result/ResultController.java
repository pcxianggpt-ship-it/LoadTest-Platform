package com.loadtest.platform.result;

import com.loadtest.platform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;
    private final ResultImageService resultImageService;

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

    @PostMapping("/api/results/{resultId}/grafana-images")
    public ApiResponse<ResultImageResponse> exportGrafanaImage(
            @PathVariable Long resultId,
            @Valid @RequestBody ResultImageRequest request
    ) {
        return ApiResponse.ok(resultImageService.exportGrafanaImage(resultId, request));
    }

    @GetMapping("/api/results/{resultId}/grafana-images")
    public ApiResponse<List<ResultImageResponse>> listGrafanaImages(@PathVariable Long resultId) {
        return ApiResponse.ok(resultImageService.listImages(resultId));
    }

    @GetMapping("/api/result-images/{imageId}/content")
    public ResponseEntity<Resource> imageContent(@PathVariable Long imageId) {
        ResultImageService.ImageContent content = resultImageService.imageContent(imageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }

    @DeleteMapping("/api/results/{resultId}")
    public ApiResponse<Void> deleteResult(@PathVariable Long resultId) {
        resultService.deleteResult(resultId);
        return ApiResponse.ok(null);
    }
}
