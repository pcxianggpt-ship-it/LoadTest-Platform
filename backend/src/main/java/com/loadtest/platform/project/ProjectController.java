package com.loadtest.platform.project;

import com.loadtest.platform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ApiResponse<ProjectResponse> createProject(
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        return ApiResponse.ok(projectService.createProject(request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> listProjects() {
        return ApiResponse.ok(projectService.listProjects());
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> getProject(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.getProject(projectId));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ApiResponse.ok(null);
    }
}
