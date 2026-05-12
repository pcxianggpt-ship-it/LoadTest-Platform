package com.loadtest.platform.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final TestTaskMapper testTaskMapper;
    private final TestExecutionMapper testExecutionMapper;

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
                .eq(TestExecution::getStatus, "scheduled")
                .le(TestExecution::getScheduledAt, now);
        for (TestExecution execution : testExecutionMapper.selectList(wrapper)) {
            execution.setStatus("pending");
            execution.setUpdatedAt(now);
            testExecutionMapper.updateById(execution);
        }
    }

    public void runOnePendingExecution() {
        // Real JMeter execution is wired in the next iteration of this module.
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
