package com.loadtest.platform.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.jmeter.JMeterCommand;
import com.loadtest.platform.jmeter.JMeterCommandBuilder;
import com.loadtest.platform.projectconfig.JMeterServer;
import com.loadtest.platform.projectconfig.JMeterServerMapper;
import com.loadtest.platform.ssh.SshCommandResult;
import com.loadtest.platform.ssh.SshCommandRunner;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import com.loadtest.platform.task.TestTaskStep;
import com.loadtest.platform.task.TestTaskStepMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskStepMapper testTaskStepMapper;
    private final TestExecutionMapper testExecutionMapper;
    private final TestExecutionStepMapper testExecutionStepMapper;
    private final JMeterServerMapper jMeterServerMapper;
    private final JMeterCommandBuilder jMeterCommandBuilder;
    private final SshCommandRunner sshCommandRunner;

    @Transactional
    public ExecutionResponse createManualExecution(Long taskId) {
        TestTask task = getTaskOrThrow(taskId);
        TestExecution execution = newExecution(task, "manual", "pending", null);
        testExecutionMapper.insert(execution);
        return ExecutionResponse.from(execution);
    }

    @Transactional
    public ExecutionResponse createScheduledExecution(Long taskId, ExecutionRequest request) {
        TestTask task = getTaskOrThrow(taskId);
        TestExecution execution = newExecution(task, "scheduled", "scheduled", request.getScheduledAt());
        testExecutionMapper.insert(execution);
        return ExecutionResponse.from(execution);
    }

    public List<ExecutionResponse> listExecutions(Long projectId) {
        LambdaQueryWrapper<TestExecution> wrapper = new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getProjectId, projectId)
                .orderByDesc(TestExecution::getId);
        return testExecutionMapper.selectList(wrapper).stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    public ExecutionResponse getExecution(Long executionId) {
        return ExecutionResponse.from(getExecutionOrThrow(executionId));
    }

    @Transactional
    public ExecutionResponse cancelExecution(Long executionId) {
        TestExecution execution = getExecutionOrThrow(executionId);
        if (!List.of("scheduled", "pending").contains(execution.getStatus())) {
            throw new IllegalArgumentException("only scheduled or pending execution can be cancelled");
        }
        execution.setStatus("cancelled");
        execution.setUpdatedAt(OffsetDateTime.now().toString());
        testExecutionMapper.updateById(execution);
        return ExecutionResponse.from(execution);
    }

    @Transactional
    public void promoteDueScheduledExecutions() {
        String now = OffsetDateTime.now().toString();
        LambdaQueryWrapper<TestExecution> wrapper = new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getStatus, "scheduled");
        for (TestExecution execution : testExecutionMapper.selectList(wrapper)) {
            if (!isDue(execution.getScheduledAt(), now)) {
                continue;
            }
            execution.setStatus("pending");
            execution.setUpdatedAt(now);
            testExecutionMapper.updateById(execution);
        }
    }

    static boolean isDue(String scheduledAt, String now) {
        try {
            return !OffsetDateTime.parse(scheduledAt).toInstant()
                    .isAfter(OffsetDateTime.parse(now).toInstant());
        } catch (Exception exception) {
            return scheduledAt != null && now != null && scheduledAt.compareTo(now) <= 0;
        }
    }

    public void runOnePendingExecution() {
        if (hasRunningExecution()) {
            return;
        }
        TestExecution execution = nextPendingExecution();
        if (execution == null) {
            return;
        }

        String startedAt = OffsetDateTime.now().toString();
        execution.setStatus("running");
        execution.setStartedAt(startedAt);
        execution.setUpdatedAt(startedAt);
        testExecutionMapper.updateById(execution);

        try {
            JMeterServer server = getJMeterServerOrThrow(execution.getProjectId());
            List<TestTaskStep> steps = enabledSteps(execution.getTaskId());
            if (steps.isEmpty()) {
                throw new IllegalStateException("task has no enabled steps");
            }
            for (TestTaskStep step : steps) {
                execution.setCurrentStepOrder(step.getStepOrder());
                execution.setUpdatedAt(OffsetDateTime.now().toString());
                testExecutionMapper.updateById(execution);

                TestExecutionStep executionStep = createExecutionStep(execution, step);
                runStep(server, executionStep, step);
                if (!"success".equals(executionStep.getStatus())) {
                    finishExecution(execution, "failed", executionStep.getErrorMessage());
                    return;
                }
            }
            finishExecution(execution, "success", null);
        } catch (Exception exception) {
            finishExecution(execution, "failed", exception.getMessage());
        }
    }

    private boolean hasRunningExecution() {
        Long count = testExecutionMapper.selectCount(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getStatus, "running"));
        return count != null && count > 0;
    }

    private TestExecution nextPendingExecution() {
        return testExecutionMapper.selectOne(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getStatus, "pending")
                .orderByAsc(TestExecution::getId)
                .last("limit 1"));
    }

    private List<TestTaskStep> enabledSteps(Long taskId) {
        return testTaskStepMapper.selectList(new LambdaQueryWrapper<TestTaskStep>()
                .eq(TestTaskStep::getTaskId, taskId)
                .eq(TestTaskStep::getEnabled, true)
                .orderByAsc(TestTaskStep::getStepOrder));
    }

    private JMeterServer getJMeterServerOrThrow(Long projectId) {
        JMeterServer server = jMeterServerMapper.selectOne(new LambdaQueryWrapper<JMeterServer>()
                .eq(JMeterServer::getProjectId, projectId)
                .eq(JMeterServer::getStatus, "active")
                .last("limit 1"));
        if (server == null) {
            throw new NotFoundException("jmeter server not found");
        }
        return server;
    }

    private TestExecutionStep createExecutionStep(TestExecution execution, TestTaskStep taskStep) {
        String now = OffsetDateTime.now().toString();
        TestExecutionStep executionStep = new TestExecutionStep();
        executionStep.setExecutionId(execution.getId());
        executionStep.setTaskStepId(taskStep.getId());
        executionStep.setStepOrder(taskStep.getStepOrder());
        executionStep.setJmxFile(taskStep.getJmxFile());
        executionStep.setStatus("running");
        executionStep.setStartedAt(now);
        executionStep.setCreatedAt(now);
        executionStep.setUpdatedAt(now);
        testExecutionStepMapper.insert(executionStep);
        return executionStep;
    }

    private void runStep(JMeterServer server, TestExecutionStep executionStep, TestTaskStep taskStep) {
        JMeterCommand command = jMeterCommandBuilder.build(server, taskStep, executionStep.getExecutionId());
        executionStep.setCommand(command.getCommand());
        executionStep.setJtlPath(command.getJtlPath());
        testExecutionStepMapper.updateById(executionStep);

        String errorMessage = null;
        try {
            SshCommandResult result = runCommand(server, taskStep, command);
            executionStep.setExitCode(result.getExitCode());
            executionStep.setSshLog(combineLog(result));
            if (result.getExitCode() == 0) {
                executionStep.setStatus("success");
            } else {
                executionStep.setStatus("failed");
                errorMessage = "JMeter command failed with exit code " + result.getExitCode();
                executionStep.setErrorMessage(errorMessage);
            }
        } catch (Exception exception) {
            executionStep.setStatus("failed");
            errorMessage = exception.getMessage();
            executionStep.setErrorMessage(errorMessage);
        }
        String endedAt = OffsetDateTime.now().toString();
        executionStep.setEndedAt(endedAt);
        executionStep.setDurationSeconds(durationSeconds(executionStep.getStartedAt(), endedAt));
        executionStep.setUpdatedAt(endedAt);
        if (errorMessage != null && executionStep.getErrorMessage() == null) {
            executionStep.setErrorMessage(errorMessage);
        }
        testExecutionStepMapper.updateById(executionStep);
    }

    private SshCommandResult runCommand(JMeterServer server, TestTaskStep taskStep, JMeterCommand command) throws Exception {
        if (!"password".equals(server.getSshAuthType())) {
            throw new IllegalArgumentException("only password ssh auth is supported in MVP");
        }
        int timeoutSeconds = taskStep.getDurationSeconds() == null ? 120 : taskStep.getDurationSeconds() + 120;
        return sshCommandRunner.runWithPassword(
                server.getHost(),
                server.getSshPort(),
                server.getSshUsername(),
                server.getSshPasswordEncrypted(),
                command.getCommand(),
                Duration.ofSeconds(timeoutSeconds)
        );
    }

    private String combineLog(SshCommandResult result) {
        return "stdout:\n" + nullToEmpty(result.getStdout()) + "\nstderr:\n" + nullToEmpty(result.getStderr());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void finishExecution(TestExecution execution, String status, String errorMessage) {
        String endedAt = OffsetDateTime.now().toString();
        execution.setStatus(status);
        execution.setEndedAt(endedAt);
        execution.setDurationSeconds(durationSeconds(execution.getStartedAt(), endedAt));
        execution.setErrorMessage(errorMessage);
        execution.setUpdatedAt(endedAt);
        testExecutionMapper.updateById(execution);
    }

    private Integer durationSeconds(String startedAt, String endedAt) {
        try {
            long seconds = Duration.between(OffsetDateTime.parse(startedAt), OffsetDateTime.parse(endedAt)).toSeconds();
            return Math.toIntExact(Math.max(0, seconds));
        } catch (Exception exception) {
            return null;
        }
    }

    private TestExecution newExecution(
            TestTask task,
            String triggerType,
            String status,
            String scheduledAt
    ) {
        String now = OffsetDateTime.now().toString();
        TestExecution execution = new TestExecution();
        execution.setProjectId(task.getProjectId());
        execution.setTaskId(task.getId());
        execution.setExecutionName(task.getName() + "-" + now);
        execution.setTriggerType(triggerType);
        execution.setScheduledAt(scheduledAt);
        execution.setStatus(status);
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        return execution;
    }

    private TestTask getTaskOrThrow(Long taskId) {
        TestTask task = testTaskMapper.selectById(taskId);
        if (task == null) {
            throw new NotFoundException("task not found");
        }
        return task;
    }

    private TestExecution getExecutionOrThrow(Long executionId) {
        TestExecution execution = testExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw new NotFoundException("execution not found");
        }
        return execution;
    }
}
