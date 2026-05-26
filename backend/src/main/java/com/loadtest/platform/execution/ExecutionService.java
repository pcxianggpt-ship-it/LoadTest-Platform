package com.loadtest.platform.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.cleanup.DeletionService;
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
    private final DeletionService deletionService;

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
    public void deleteExecution(Long executionId) {
        getExecutionOrThrow(executionId);
        deletionService.deleteExecution(executionId);
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
            startStep(server, execution, steps.get(0));
        } catch (Exception exception) {
            finishExecution(execution, "failed", exception.getMessage());
        }
    }

    public void checkRunningExecutions() {
        List<TestExecution> runningExecutions = testExecutionMapper.selectList(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getStatus, "running")
                .orderByAsc(TestExecution::getId));
        for (TestExecution execution : runningExecutions) {
            checkRunningExecution(execution);
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

    private void startStep(JMeterServer server, TestExecution execution, TestTaskStep taskStep) {
        execution.setCurrentStepOrder(taskStep.getStepOrder());
        execution.setUpdatedAt(OffsetDateTime.now().toString());
        testExecutionMapper.updateById(execution);

        TestExecutionStep executionStep = createExecutionStep(execution, taskStep);
        JMeterCommand command = jMeterCommandBuilder.build(server, taskStep, executionStep.getExecutionId());
        executionStep.setCommand(command.getCommand());
        executionStep.setJtlPath(command.getJtlPath());
        executionStep.setRemoteRunDir(remoteRunDir(server, executionStep));
        testExecutionStepMapper.updateById(executionStep);

        try {
            SshCommandResult result = startRemoteCommand(server, command, executionStep.getRemoteRunDir());
            executionStep.setSshLog(combineLog(result));
            if (result.getExitCode() == 0) {
                executionStep.setRemotePid(firstLine(result.getStdout()));
            } else {
                executionStep.setStatus("failed");
                executionStep.setExitCode(result.getExitCode());
                executionStep.setErrorMessage("failed to start JMeter command with exit code " + result.getExitCode());
                finishExecution(execution, "failed", executionStep.getErrorMessage());
            }
        } catch (Exception exception) {
            executionStep.setStatus("failed");
            executionStep.setErrorMessage(exception.getMessage());
            finishExecution(execution, "failed", exception.getMessage());
        }
        executionStep.setUpdatedAt(OffsetDateTime.now().toString());
        testExecutionStepMapper.updateById(executionStep);
    }

    private void checkRunningExecution(TestExecution execution) {
        TestExecutionStep runningStep = runningStep(execution.getId());
        if (runningStep == null) {
            finishExecution(execution, "failed", "running execution has no running step");
            return;
        }

        try {
            JMeterServer server = getJMeterServerOrThrow(execution.getProjectId());
            SshCommandResult result = checkRemoteCommand(server, runningStep);
            String stdout = nullToEmpty(result.getStdout());
            runningStep.setSshLog(combineLog(result));
            if (stdout.startsWith("RUNNING")) {
                runningStep.setUpdatedAt(OffsetDateTime.now().toString());
                testExecutionStepMapper.updateById(runningStep);
                return;
            }
            if (stdout.startsWith("DONE")) {
                finishRunningStep(execution, runningStep, stdout);
                return;
            }
            failRunningStep(execution, runningStep, "Remote JMeter process stopped before completion");
        } catch (Exception exception) {
            failRunningStep(execution, runningStep, exception.getMessage());
        }
    }

    private TestExecutionStep runningStep(Long executionId) {
        return testExecutionStepMapper.selectOne(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId)
                .eq(TestExecutionStep::getStatus, "running")
                .orderByAsc(TestExecutionStep::getStepOrder)
                .last("limit 1"));
    }

    private void finishRunningStep(TestExecution execution, TestExecutionStep runningStep, String statusOutput) {
        Integer exitCode = parseExitCode(statusOutput);
        String endedAt = OffsetDateTime.now().toString();
        runningStep.setExitCode(exitCode);
        runningStep.setEndedAt(endedAt);
        runningStep.setDurationSeconds(durationSeconds(runningStep.getStartedAt(), endedAt));
        runningStep.setUpdatedAt(endedAt);
        if (exitCode != null && exitCode == 0) {
            runningStep.setStatus("success");
            testExecutionStepMapper.updateById(runningStep);
            startNextStepOrFinish(execution, runningStep.getStepOrder());
            return;
        }
        runningStep.setStatus("failed");
        runningStep.setErrorMessage("JMeter command failed with exit code " + exitCode);
        testExecutionStepMapper.updateById(runningStep);
        finishExecution(execution, "failed", runningStep.getErrorMessage());
    }

    private void failRunningStep(TestExecution execution, TestExecutionStep runningStep, String errorMessage) {
        String endedAt = OffsetDateTime.now().toString();
        runningStep.setStatus("failed");
        runningStep.setEndedAt(endedAt);
        runningStep.setDurationSeconds(durationSeconds(runningStep.getStartedAt(), endedAt));
        runningStep.setErrorMessage(errorMessage);
        runningStep.setUpdatedAt(endedAt);
        testExecutionStepMapper.updateById(runningStep);
        finishExecution(execution, "failed", errorMessage);
    }

    private void startNextStepOrFinish(TestExecution execution, Integer completedStepOrder) {
        List<TestTaskStep> steps = enabledSteps(execution.getTaskId());
        for (TestTaskStep step : steps) {
            if (step.getStepOrder() != null && completedStepOrder != null
                    && step.getStepOrder() > completedStepOrder) {
                startStep(getJMeterServerOrThrow(execution.getProjectId()), execution, step);
                return;
            }
        }
        finishExecution(execution, "success", null);
    }

    private SshCommandResult startRemoteCommand(JMeterServer server, JMeterCommand command, String runDir) throws Exception {
        if (!"password".equals(server.getSshAuthType())) {
            throw new IllegalArgumentException("only password ssh auth is supported in MVP");
        }
        return sshCommandRunner.runWithPassword(
                server.getHost(),
                server.getSshPort(),
                server.getSshUsername(),
                server.getSshPasswordEncrypted(),
                startCommand(command, runDir),
                Duration.ofSeconds(30)
        );
    }

    private SshCommandResult checkRemoteCommand(JMeterServer server, TestExecutionStep step) throws Exception {
        if (!"password".equals(server.getSshAuthType())) {
            throw new IllegalArgumentException("only password ssh auth is supported in MVP");
        }
        return sshCommandRunner.runWithPassword(
                server.getHost(),
                server.getSshPort(),
                server.getSshUsername(),
                server.getSshPasswordEncrypted(),
                checkCommand(step),
                Duration.ofSeconds(30)
        );
    }

    private String combineLog(SshCommandResult result) {
        return "stdout:\n" + nullToEmpty(result.getStdout()) + "\nstderr:\n" + nullToEmpty(result.getStderr());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String remoteRunDir(JMeterServer server, TestExecutionStep step) {
        return joinPath(server.getLogDir(), "execution_" + step.getExecutionId() + "_step_" + step.getStepOrder() + "_run");
    }

    private String startCommand(JMeterCommand command, String runDir) {
        String stdoutPath = joinPath(runDir, "stdout.log");
        String stderrPath = joinPath(runDir, "stderr.log");
        String exitCodePath = joinPath(runDir, "exit_code");
        String donePath = joinPath(runDir, "done");
        String script = command.getCommand()
                + " > " + shellQuote(stdoutPath)
                + " 2> " + shellQuote(stderrPath)
                + "; code=$?"
                + "; echo $code > " + shellQuote(exitCodePath)
                + "; touch " + shellQuote(donePath);
        return "mkdir -p " + shellQuote(runDir)
                + " && (nohup sh -c " + shellQuote(script)
                + " >/dev/null 2>&1 & echo $!)";
    }

    private String checkCommand(TestExecutionStep step) {
        String runDir = step.getRemoteRunDir();
        String donePath = joinPath(runDir, "done");
        String exitCodePath = joinPath(runDir, "exit_code");
        String stdoutPath = joinPath(runDir, "stdout.log");
        String stderrPath = joinPath(runDir, "stderr.log");
        String pid = step.getRemotePid();
        return "if [ -f " + shellQuote(donePath) + " ]; then "
                + "echo DONE; cat " + shellQuote(exitCodePath) + "; "
                + "echo stdout:; cat " + shellQuote(stdoutPath) + " 2>/dev/null; "
                + "echo stderr:; cat " + shellQuote(stderrPath) + " 2>/dev/null; "
                + "elif kill -0 " + shellQuote(pid) + " 2>/dev/null; then "
                + "echo RUNNING; "
                + "else echo LOST; fi";
    }

    private String firstLine(String value) {
        if (value == null) {
            return null;
        }
        return value.lines().findFirst().map(String::trim).orElse(null);
    }

    private Integer parseExitCode(String statusOutput) {
        return statusOutput.lines()
                .skip(1)
                .findFirst()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .orElse(null);
    }

    private String joinPath(String base, String child) {
        String normalizedBase = base == null ? "" : base.replaceAll("/+$", "");
        String normalizedChild = child == null ? "" : child.replaceAll("^/+", "");
        return normalizedBase + "/" + normalizedChild;
    }

    private String shellQuote(String value) {
        return "'" + nullToEmpty(value).replace("'", "'\"'\"'") + "'";
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
