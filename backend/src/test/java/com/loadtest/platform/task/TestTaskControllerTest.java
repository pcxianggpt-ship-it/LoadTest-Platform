package com.loadtest.platform.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TestTaskControllerTest {

    private static final Path DB_PATH = Path.of("target", "test-task-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsTaskWithOneJmxStepAndReadsIt() throws Exception {
        Long projectId = createProject();
        String body = """
                {
                  "name": "订单查询压测",
                  "description": "验证订单查询接口",
                  "defaultSaveJtl": false,
                  "step": {
                    "stepName": "订单查询",
                    "jmxFile": "order_query.jmx",
                    "threads": 100,
                    "durationSeconds": 600,
                    "rampUpSeconds": 60,
                    "saveJtl": false,
                    "jmeterArgsJson": "{\\"host\\":\\"api.example.test\\"}"
                  }
                }
                """;

        String response = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单查询压测"))
                .andExpect(jsonPath("$.data.steps[0].jmxFile").value("order_query.jmx"))
                .andExpect(jsonPath("$.data.steps[0].threads").value(100))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = extractId(response);

        mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].steps[0].stepOrder").value(1));

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单查询压测"))
                .andExpect(jsonPath("$.data.steps[0].durationSeconds").value(600));
    }

    @Test
    void rejectsInvalidStepValues() throws Exception {
        Long projectId = createProject();
        String body = """
                {
                  "name": "错误任务",
                  "step": {
                    "stepName": "错误步骤",
                    "jmxFile": "order_query.txt",
                    "threads": 0,
                    "durationSeconds": 0,
                    "rampUpSeconds": -1
                  }
                }
                """;

        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsNotFoundWhenProjectDoesNotExist() throws Exception {
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

        mockMvc.perform(post("/api/projects/999/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deletesTask() throws Exception {
        Long projectId = createProject();
        Long taskId = createTask(projectId);

        mockMvc.perform(delete("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesTaskAndItsJmxStep() throws Exception {
        Long projectId = createProject();
        Long taskId = createTask(projectId);
        String body = """
                {
                  "name": "订单查询压测-编辑",
                  "description": "更新后的任务说明",
                  "defaultSaveJtl": true,
                  "step": {
                    "stepName": "订单查询-编辑",
                    "jmxFile": "checkout.jmx",
                    "threads": 200,
                    "durationSeconds": 900,
                    "rampUpSeconds": 120,
                    "saveJtl": true,
                    "jmeterArgsJson": "{\\"host\\":\\"api.updated.test\\"}"
                  }
                }
                """;

        mockMvc.perform(put("/api/tasks/{taskId}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单查询压测-编辑"))
                .andExpect(jsonPath("$.data.description").value("更新后的任务说明"))
                .andExpect(jsonPath("$.data.defaultSaveJtl").value(true))
                .andExpect(jsonPath("$.data.steps[0].stepName").value("订单查询-编辑"))
                .andExpect(jsonPath("$.data.steps[0].jmxFile").value("checkout.jmx"))
                .andExpect(jsonPath("$.data.steps[0].threads").value(200))
                .andExpect(jsonPath("$.data.steps[0].durationSeconds").value(900))
                .andExpect(jsonPath("$.data.steps[0].rampUpSeconds").value(120))
                .andExpect(jsonPath("$.data.steps[0].saveJtl").value(true));

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单查询压测-编辑"))
                .andExpect(jsonPath("$.data.steps[0].jmxFile").value("checkout.jmx"));
    }

    private Long createProject() throws Exception {
        String body = """
                {
                  "name": "订单系统压测",
                  "environmentName": "test"
                }
                """;
        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
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
        String response = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractId(response);
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
