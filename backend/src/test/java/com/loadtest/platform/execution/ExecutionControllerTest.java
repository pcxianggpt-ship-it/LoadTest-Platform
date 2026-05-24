package com.loadtest.platform.execution;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.ssh.SshCommandResult;
import com.loadtest.platform.ssh.SshCommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private TestExecutionMapper testExecutionMapper;

    @Autowired
    private TestExecutionStepMapper testExecutionStepMapper;

    @MockBean
    private SshCommandRunner sshCommandRunner;

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

        Long executionId = extractId(mockMvc.perform(post("/api/tasks/{taskId}/executions/scheduled", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggerType").value("scheduled"))
                .andExpect(jsonPath("$.data.status").value("scheduled"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-05-12T20:00:00+08:00"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/executions/{executionId}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    @Test
    void returnsNotFoundWhenTaskDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/tasks/999/executions/manual"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deletesExecution() throws Exception {
        Long taskId = createTask(createProject());
        Long executionId = createManualExecution(taskId);

        mockMvc.perform(delete("/api/executions/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/executions/{executionId}", executionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void runsOnePendingExecutionAndMarksSuccess() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId, true);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("jmeter done")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("success");
        assertThat(execution.getStartedAt()).isNotBlank();
        assertThat(execution.getEndedAt()).isNotBlank();
        assertThat(execution.getDurationSeconds()).isNotNull();
        List<TestExecutionStep> steps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStatus()).isEqualTo("success");
        assertThat(steps.get(0).getCommand()).contains("order_query.jmx");
        assertThat(steps.get(0).getJtlPath()).contains("execution_" + executionId + "_step_1.jtl");
        assertThat(steps.get(0).getSshLog()).contains("jmeter done");
    }

    @Test
    void marksExecutionFailedWhenJMeterCommandFails() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(1)
                        .stdout("")
                        .stderr("jmeter failed")
                        .build());

        executionService.runOnePendingExecution();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("failed");
        assertThat(execution.getErrorMessage()).contains("exit code 1");
        List<TestExecutionStep> steps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStatus()).isEqualTo("failed");
        assertThat(steps.get(0).getErrorMessage()).contains("exit code 1");
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
        return createTask(projectId, false);
    }

    private Long createTask(Long projectId, boolean saveJtl) throws Exception {
        String body = """
                {
                  "name": "订单查询压测",
                  "step": {
                    "stepName": "订单查询",
                    "jmxFile": "order_query.jmx",
                    "threads": 100,
                    "durationSeconds": 600,
                    "rampUpSeconds": 60,
                    "saveJtl": %s
                  }
                }
                """.formatted(saveJtl);
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createManualExecution(Long taskId) throws Exception {
        return extractId(mockMvc.perform(post("/api/tasks/{taskId}/executions/manual", taskId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void createJMeterServer(Long projectId) throws Exception {
        String body = """
                {
                  "name": "jmeter-linux",
                  "host": "10.0.0.10",
                  "sshPort": 22,
                  "sshUsername": "root",
                  "sshAuthType": "password",
                  "sshPasswordEncrypted": "secret",
                  "jmeterHome": "/opt/apache-jmeter",
                  "scriptDir": "/opt/jmeter/scripts",
                  "resultDir": "/opt/jmeter/results",
                  "logDir": "/opt/jmeter/logs"
                }
                """;
        mockMvc.perform(put("/api/projects/{projectId}/jmeter-server", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
