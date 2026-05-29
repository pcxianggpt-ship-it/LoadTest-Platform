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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.cleanup.CleanupRun;
import com.loadtest.platform.cleanup.CleanupRunMapper;
import com.loadtest.platform.cleanup.CleanupRunStep;
import com.loadtest.platform.cleanup.CleanupRunStepMapper;
import com.loadtest.platform.cleanup.CleanupSqlExecutor;
import com.loadtest.platform.ssh.SshCommandResult;
import com.loadtest.platform.ssh.SshCommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
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

    @Autowired
    private CleanupRunStepMapper cleanupRunStepMapper;

    @Autowired
    private CleanupRunMapper cleanupRunMapper;

    @MockBean
    private SshCommandRunner sshCommandRunner;

    @MockBean
    private CleanupSqlExecutor cleanupSqlExecutor;

    @BeforeEach
    void clearActiveExecutions() {
        List<TestExecution> activeExecutions = testExecutionMapper.selectList(new LambdaQueryWrapper<TestExecution>()
                .in(TestExecution::getStatus, List.of("pending", "scheduled", "running")));
        for (TestExecution execution : activeExecutions) {
            execution.setStatus("cancelled");
            testExecutionMapper.updateById(execution);
        }
        List<TestExecutionStep> activeSteps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getStatus, "running"));
        for (TestExecutionStep step : activeSteps) {
            step.setStatus("cancelled");
            testExecutionStepMapper.updateById(step);
        }
    }

    @Test
    void createsManualExecutionAndListsIt() throws Exception {
        Long projectId = createProject();
        Long taskId = createTask(projectId);

        mockMvc.perform(post("/api/tasks/{taskId}/executions/manual", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.triggerType").value("manual"))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.cleanupStatus").value("none"));

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
                        .stdout("4242\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("DONE\n0\nstdout:\njmeter done\nstderr:\n")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();
        executionService.checkRunningExecutions();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("success");
        assertThat(execution.getCleanupStatus()).isEqualTo("skipped");
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
    void runsCleanupAfterSuccessfulExecution() throws Exception {
        Long projectId = createProject();
        Long databaseId = createBusinessDatabase(projectId);
        Long cleanupPlanId = createCleanupPlan(projectId, databaseId);
        createJMeterServer(projectId);
        Long taskId = createTask(projectId, true, cleanupPlanId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("4244\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("DONE\n0\nstdout:\njmeter done\nstderr:\n")
                        .stderr("")
                        .build());
        when(cleanupSqlExecutor.execute(any(), anyString())).thenReturn(2);

        executionService.runOnePendingExecution();
        executionService.checkRunningExecutions();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("success");
        assertThat(execution.getCleanupStatus()).isEqualTo("success");
        assertThat(execution.getCleanupStartedAt()).isNotBlank();
        assertThat(execution.getCleanupEndedAt()).isNotBlank();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(cleanupSqlExecutor, times(2)).execute(any(), sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues()).containsExactly(
                "delete from t_order where test_flag = 1",
                "update account set amount = 100 where user_id = 1");

        List<CleanupRunStep> cleanupSteps = cleanupStepsByExecution(executionId);
        assertThat(cleanupSteps).hasSize(2);
        assertThat(cleanupSteps).allSatisfy(step -> {
            assertThat(step.getStatus()).isEqualTo("success");
            assertThat(step.getAffectedRows()).isEqualTo(2);
        });
    }

    @Test
    void stopsCleanupOnFailureAndRetriesIt() throws Exception {
        Long projectId = createProject();
        Long databaseId = createBusinessDatabase(projectId);
        Long cleanupPlanId = createCleanupPlan(projectId, databaseId);
        createJMeterServer(projectId);
        Long taskId = createTask(projectId, true, cleanupPlanId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("4245\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("DONE\n0\nstdout:\njmeter done\nstderr:\n")
                        .stderr("")
                        .build());
        when(cleanupSqlExecutor.execute(any(), anyString()))
                .thenReturn(1)
                .thenThrow(new RuntimeException("cleanup boom"))
                .thenReturn(1)
                .thenReturn(1);

        executionService.runOnePendingExecution();
        executionService.checkRunningExecutions();

        TestExecution failedCleanup = testExecutionMapper.selectById(executionId);
        assertThat(failedCleanup.getStatus()).isEqualTo("success");
        assertThat(failedCleanup.getCleanupStatus()).isEqualTo("failed");
        assertThat(failedCleanup.getCleanupErrorMessage()).contains("cleanup boom");

        List<CleanupRunStep> failedSteps = cleanupStepsByExecution(executionId);
        assertThat(failedSteps).hasSize(2);
        assertThat(failedSteps.get(0).getStatus()).isEqualTo("success");
        assertThat(failedSteps.get(1).getStatus()).isEqualTo("failed");

        mockMvc.perform(post("/api/executions/{executionId}/cleanup/retry", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.cleanupStatus").value("success"));

        TestExecution retriedCleanup = testExecutionMapper.selectById(executionId);
        assertThat(retriedCleanup.getCleanupStatus()).isEqualTo("success");
        assertThat(cleanupStepsByExecution(executionId)).hasSize(4);
    }

    @Test
    void marksExecutionFailedWhenJMeterCommandFails() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("4243\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("DONE\n1\nstdout:\nstderr:\njmeter failed\n")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();
        executionService.checkRunningExecutions();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("failed");
        assertThat(execution.getErrorMessage()).contains("exit code 1");
        List<TestExecutionStep> steps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStatus()).isEqualTo("failed");
        assertThat(steps.get(0).getErrorMessage()).contains("exit code 1");
    }

    @Test
    void startsJMeterInBackgroundAndKeepsExecutionRunningUntilRemoteDoneFileExists() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("9876\n")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("running");
        assertThat(execution.getEndedAt()).isNull();

        ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
        verify(sshCommandRunner).runWithPassword(anyString(), anyInt(), anyString(), anyString(), commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue()).contains("nohup sh -c");
        assertThat(commandCaptor.getValue()).contains("exit_code");
        assertThat(commandCaptor.getValue()).contains("done");
        assertThat(commandCaptor.getValue()).contains("& echo $!");

        List<TestExecutionStep> steps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStatus()).isEqualTo("running");
        assertThat(steps.get(0).getRemotePid()).isEqualTo("9876");
        assertThat(steps.get(0).getRemoteRunDir()).contains("execution_" + executionId + "_step_1_run");

        execution.setStatus("cancelled");
        testExecutionMapper.updateById(execution);
    }

    @Test
    void stopsRunningExecutionAndMarksItCancelled() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("9878\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("STOPPED\n")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();

        mockMvc.perform(post("/api/executions/{executionId}/stop", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("cancelled");
        assertThat(execution.getEndedAt()).isNotBlank();
        assertThat(execution.getErrorMessage()).contains("stopped by user");

        List<TestExecutionStep> steps = testExecutionStepMapper.selectList(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStatus()).isEqualTo("cancelled");
        assertThat(steps.get(0).getEndedAt()).isNotBlank();

        ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
        verify(sshCommandRunner, org.mockito.Mockito.times(2))
                .runWithPassword(anyString(), anyInt(), anyString(), anyString(), commandCaptor.capture(), any());
        assertThat(commandCaptor.getAllValues().get(1)).contains("kill -TERM -- -9878");
        assertThat(commandCaptor.getAllValues().get(1)).contains("kill -KILL -- -9878");
        assertThat(commandCaptor.getAllValues().get(1)).contains("stopped");
    }

    @Test
    void rejectsStopWhenExecutionIsNotRunning() throws Exception {
        Long taskId = createTask(createProject());
        Long executionId = createManualExecution(taskId);

        mockMvc.perform(post("/api/executions/{executionId}/stop", executionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void marksRunningExecutionFailedWhenRemoteProcessDisappearsWithoutDoneFile() throws Exception {
        Long projectId = createProject();
        createJMeterServer(projectId);
        Long taskId = createTask(projectId);
        Long executionId = createManualExecution(taskId);
        when(sshCommandRunner.runWithPassword(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("9877\n")
                        .stderr("")
                        .build())
                .thenReturn(SshCommandResult.builder()
                        .exitCode(0)
                        .stdout("LOST\nstdout:\nstderr:\n")
                        .stderr("")
                        .build());

        executionService.runOnePendingExecution();
        executionService.checkRunningExecutions();

        TestExecution execution = testExecutionMapper.selectById(executionId);
        assertThat(execution.getStatus()).isEqualTo("failed");
        assertThat(execution.getErrorMessage()).contains("Remote JMeter process stopped before completion");
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
        return createTask(projectId, saveJtl, null);
    }

    private Long createTask(Long projectId, boolean saveJtl, Long cleanupPlanId) throws Exception {
        String body = """
                {
                  "name": "订单查询压测",
                  %s
                  "step": {
                    "stepName": "订单查询",
                    "jmxFile": "order_query.jmx",
                    "threads": 100,
                    "durationSeconds": 600,
                    "rampUpSeconds": 60,
                    "saveJtl": %s
                  }
                }
                """.formatted(cleanupPlanId == null ? "" : "\"cleanupPlanId\": " + cleanupPlanId + ",", saveJtl);
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

    private Long createBusinessDatabase(Long projectId) throws Exception {
        String body = """
                {
                  "name": "order-mysql",
                  "databaseType": "mysql",
                  "jdbcUrl": "jdbc:mysql://10.0.0.8:3306/orderdb",
                  "username": "tester",
                  "passwordEncrypted": "secret",
                  "status": "active"
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/business-databases", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createCleanupPlan(Long projectId, Long databaseId) throws Exception {
        String body = """
                {
                  "name": "订单数据清理",
                  "description": "删除压测订单并恢复金额",
                  "businessDatabaseId": %d,
                  "enabled": true,
                  "sqlStatements": [
                    "delete from t_order where test_flag = 1",
                    "update account set amount = 100 where user_id = 1"
                  ]
                }
                """.formatted(databaseId);
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/cleanup-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<CleanupRunStep> cleanupStepsByExecution(Long executionId) {
        List<Long> runIds = cleanupRunMapper.selectList(new LambdaQueryWrapper<CleanupRun>()
                        .eq(CleanupRun::getExecutionId, executionId)
                        .orderByAsc(CleanupRun::getId))
                .stream()
                .map(CleanupRun::getId)
                .toList();
        if (runIds.isEmpty()) {
            return List.of();
        }
        return cleanupRunStepMapper.selectList(new LambdaQueryWrapper<CleanupRunStep>()
                .in(CleanupRunStep::getCleanupRunId, runIds)
                .orderByAsc(CleanupRunStep::getId));
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
