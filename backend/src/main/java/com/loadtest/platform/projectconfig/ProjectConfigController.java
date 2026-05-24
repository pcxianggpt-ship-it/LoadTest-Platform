package com.loadtest.platform.projectconfig;

import com.loadtest.platform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectConfigController {

    private final ProjectConfigService projectConfigService;

    @PutMapping("/jmeter-server")
    public ApiResponse<JMeterServerResponse> upsertJMeterServer(
            @PathVariable Long projectId,
            @Valid @RequestBody JMeterServerRequest request
    ) {
        return ApiResponse.ok(projectConfigService.upsertJMeterServer(projectId, request));
    }

    @GetMapping("/jmeter-server")
    public ApiResponse<JMeterServerResponse> getJMeterServer(@PathVariable Long projectId) {
        return ApiResponse.ok(projectConfigService.getJMeterServer(projectId));
    }

    @GetMapping("/jmx-files")
    public ApiResponse<List<String>> listJmxFiles(@PathVariable Long projectId) {
        return ApiResponse.ok(projectConfigService.listJmxFiles(projectId));
    }

    @PutMapping("/datasources/{datasourceType}")
    public ApiResponse<DatasourceResponse> upsertDatasource(
            @PathVariable Long projectId,
            @PathVariable String datasourceType,
            @Valid @RequestBody DatasourceRequest request
    ) {
        return ApiResponse.ok(projectConfigService.upsertDatasource(projectId, datasourceType, request));
    }

    @GetMapping("/datasources")
    public ApiResponse<List<DatasourceResponse>> listDatasources(@PathVariable Long projectId) {
        return ApiResponse.ok(projectConfigService.listDatasources(projectId));
    }
}
