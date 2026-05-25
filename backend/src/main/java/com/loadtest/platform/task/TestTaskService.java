package com.loadtest.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.cleanup.DeletionService;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.project.ProjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestTaskService {

    private final ProjectMapper projectMapper;
    private final TestTaskMapper testTaskMapper;
    private final TestTaskStepMapper testTaskStepMapper;
    private final DeletionService deletionService;

    @Transactional
    public TestTaskResponse createTask(Long projectId, TestTaskRequest request) {
        ensureProjectExists(projectId);
        String now = OffsetDateTime.now().toString();
        TestTask task = new TestTask();
        task.setProjectId(projectId);
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setDefaultSaveJtl(Boolean.TRUE.equals(request.getDefaultSaveJtl()));
        task.setStatus("active");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        testTaskMapper.insert(task);

        TestTaskRequest.StepRequest stepRequest = request.getStep();
        TestTaskStep taskStep = new TestTaskStep();
        taskStep.setTaskId(task.getId());
        taskStep.setStepOrder(1);
        taskStep.setStepName(stepRequest.getStepName());
        taskStep.setJmxFile(stepRequest.getJmxFile());
        taskStep.setThreads(stepRequest.getThreads());
        taskStep.setDurationSeconds(stepRequest.getDurationSeconds());
        taskStep.setRampUpSeconds(stepRequest.getRampUpSeconds());
        taskStep.setSaveJtl(stepRequest.getSaveJtl() != null
                ? stepRequest.getSaveJtl()
                : task.getDefaultSaveJtl());
        taskStep.setJmeterArgsJson(stepRequest.getJmeterArgsJson());
        taskStep.setEnabled(true);
        taskStep.setCreatedAt(now);
        taskStep.setUpdatedAt(now);
        testTaskStepMapper.insert(taskStep);

        return TestTaskResponse.from(task, List.of(taskStep));
    }

    public List<TestTaskResponse> listTasksByProject(Long projectId) {
        ensureProjectExists(projectId);
        LambdaQueryWrapper<TestTask> wrapper = new LambdaQueryWrapper<TestTask>()
                .eq(TestTask::getProjectId, projectId)
                .orderByDesc(TestTask::getId);
        return testTaskMapper.selectList(wrapper).stream()
                .map(task -> TestTaskResponse.from(task, listSteps(task.getId())))
                .toList();
    }

    public TestTaskResponse getTask(Long taskId) {
        TestTask task = testTaskMapper.selectById(taskId);
        if (task == null) {
            throw new NotFoundException("task not found");
        }
        return TestTaskResponse.from(task, listSteps(taskId));
    }

    @Transactional
    public TestTaskResponse updateTask(Long taskId, TestTaskRequest request) {
        TestTask task = testTaskMapper.selectById(taskId);
        if (task == null) {
            throw new NotFoundException("task not found");
        }
        String now = OffsetDateTime.now().toString();
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setDefaultSaveJtl(Boolean.TRUE.equals(request.getDefaultSaveJtl()));
        task.setUpdatedAt(now);
        testTaskMapper.updateById(task);

        TestTaskRequest.StepRequest stepRequest = request.getStep();
        TestTaskStep taskStep = firstStep(taskId);
        if (taskStep == null) {
            taskStep = new TestTaskStep();
            taskStep.setTaskId(taskId);
            taskStep.setStepOrder(1);
            taskStep.setEnabled(true);
            taskStep.setCreatedAt(now);
        }
        taskStep.setStepName(stepRequest.getStepName());
        taskStep.setJmxFile(stepRequest.getJmxFile());
        taskStep.setThreads(stepRequest.getThreads());
        taskStep.setDurationSeconds(stepRequest.getDurationSeconds());
        taskStep.setRampUpSeconds(stepRequest.getRampUpSeconds());
        taskStep.setSaveJtl(stepRequest.getSaveJtl() != null
                ? stepRequest.getSaveJtl()
                : task.getDefaultSaveJtl());
        taskStep.setJmeterArgsJson(stepRequest.getJmeterArgsJson());
        taskStep.setUpdatedAt(now);
        if (taskStep.getId() == null) {
            testTaskStepMapper.insert(taskStep);
        } else {
            testTaskStepMapper.updateById(taskStep);
        }
        return TestTaskResponse.from(task, listSteps(taskId));
    }

    @Transactional
    public void deleteTask(Long taskId) {
        if (testTaskMapper.selectById(taskId) == null) {
            throw new NotFoundException("task not found");
        }
        deletionService.deleteTask(taskId);
    }

    private List<TestTaskStep> listSteps(Long taskId) {
        LambdaQueryWrapper<TestTaskStep> wrapper = new LambdaQueryWrapper<TestTaskStep>()
                .eq(TestTaskStep::getTaskId, taskId)
                .orderByAsc(TestTaskStep::getStepOrder);
        return testTaskStepMapper.selectList(wrapper);
    }

    private TestTaskStep firstStep(Long taskId) {
        LambdaQueryWrapper<TestTaskStep> wrapper = new LambdaQueryWrapper<TestTaskStep>()
                .eq(TestTaskStep::getTaskId, taskId)
                .orderByAsc(TestTaskStep::getStepOrder)
                .last("limit 1");
        return testTaskStepMapper.selectOne(wrapper);
    }

    private void ensureProjectExists(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new NotFoundException("project not found");
        }
    }
}
