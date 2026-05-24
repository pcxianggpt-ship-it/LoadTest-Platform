package com.loadtest.platform.task;

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
@RequiredArgsConstructor
public class TestTaskController {

    private final TestTaskService testTaskService;

    @PostMapping("/api/projects/{projectId}/tasks")
    public ApiResponse<TestTaskResponse> createTask(
            @PathVariable Long projectId,
            @Valid @RequestBody TestTaskRequest request
    ) {
        return ApiResponse.ok(testTaskService.createTask(projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public ApiResponse<List<TestTaskResponse>> listTasks(@PathVariable Long projectId) {
        return ApiResponse.ok(testTaskService.listTasksByProject(projectId));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ApiResponse<TestTaskResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(testTaskService.getTask(taskId));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable Long taskId) {
        testTaskService.deleteTask(taskId);
        return ApiResponse.ok(null);
    }
}
