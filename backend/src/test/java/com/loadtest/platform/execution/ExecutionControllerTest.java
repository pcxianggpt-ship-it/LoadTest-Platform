package com.loadtest.platform.execution;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerTest {

    private static final Path DB_PATH = Path.of("target", "execution-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsManualExecutionAndListsIt() throws Exception {
        Long projectId = createProject();
        Long taskId = createTask(projectId);

        mockMvc.perform(post("/api/tasks/{taskId}/executions/manual", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.triggerType").value("manual"))
                .andExpect(jsonPath("$.data.status").value("pending"));

        mockMvc.perform(get("/api/projects/{projectId}/executions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("pending"));
    }

    @Test
    void createsScheduledExecutionAndCancelsIt() throws Exception {
        Long taskId = createTask(createProject());
        String body = """
                {
                  "scheduledAt": "2026-05-12T20:00:00+08:00"
                }
                """;

        mockMvc.perform(post("/api/tasks/{taskId}/executions/scheduled", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggerType").value("scheduled"))
                .andExpect(jsonPath("$.data.status").value("scheduled"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-05-12T20:00:00+08:00"));

        mockMvc.perform(post("/api/executions/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    @Test
    void returnsNotFoundWhenTaskDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/tasks/999/executions/manual"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createProject() throws Exception {
        String body = """
                {
                  "name": "订单系统压测",
                  "environmentName": "test"
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createTask(Long projectId) throws Exception {
        String body = """
                {
                  "name": "订单查询压测",
                  "step": {
                    "stepName": "订单查询",
                    "jmxFile": "order_query.jmx",
                    "threads": 100,
                    "durationSeconds": 600,
                    "rampUpSeconds": 60
                  }
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
