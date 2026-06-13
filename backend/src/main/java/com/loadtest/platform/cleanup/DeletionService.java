package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.execution.TestExecutionStep;
import com.loadtest.platform.execution.TestExecutionStepMapper;
import com.loadtest.platform.project.ProjectMapper;
import com.loadtest.platform.projectconfig.JMeterServer;
import com.loadtest.platform.projectconfig.JMeterServerMapper;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import com.loadtest.platform.projectconfig.ProjectDatasourceMapper;
import com.loadtest.platform.report.TestReport;
import com.loadtest.platform.report.TestReportMapper;
import com.loadtest.platform.result.TestResult;
import com.loadtest.platform.result.TestResultImage;
import com.loadtest.platform.result.TestResultImageMapper;
import com.loadtest.platform.result.TestResultMapper;
import com.loadtest.platform.result.TestResultMetric;
import com.loadtest.platform.result.TestResultMetricMapper;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import com.loadtest.platform.task.TestTaskStep;
import com.loadtest.platform.task.TestTaskStepMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DeletionService {

    private final ProjectMapper projectMapper;
    private final JMeterServerMapper jMeterServerMapper;
    private final ProjectDatasourceMapper projectDatasourceMapper;
    private final TestTaskMapper testTaskMapper;
    private final TestTaskStepMapper testTaskStepMapper;
    private final TestExecutionMapper testExecutionMapper;
    private final TestExecutionStepMapper testExecutionStepMapper;
    private final TestResultMapper testResultMapper;
    private final TestResultMetricMapper testResultMetricMapper;
    private final TestResultImageMapper testResultImageMapper;
    private final TestReportMapper testReportMapper;
    private final BusinessDatabaseMapper businessDatabaseMapper;
    private final CleanupPlanMapper cleanupPlanMapper;
    private final CleanupPlanSqlMapper cleanupPlanSqlMapper;
    private final CleanupRunMapper cleanupRunMapper;
    private final CleanupRunStepMapper cleanupRunStepMapper;

    @Value("${loadtest.grafana.image-dir:./data/grafana-images}")
    private String imageDir;

    @Transactional
    public void deleteProject(Long projectId) {
        deleteReportsByProject(projectId);
        for (TestResult result : resultsByProject(projectId)) {
            deleteResult(result.getId());
        }
        for (TestExecution execution : executionsByProject(projectId)) {
            deleteExecution(execution.getId());
        }
        for (TestTask task : tasksByProject(projectId)) {
            deleteTask(task.getId());
        }
        for (CleanupPlan plan : cleanupPlansByProject(projectId)) {
            deleteCleanupPlan(plan.getId());
        }
        businessDatabaseMapper.delete(new LambdaQueryWrapper<BusinessDatabase>()
                .eq(BusinessDatabase::getProjectId, projectId));
        jMeterServerMapper.delete(new LambdaQueryWrapper<JMeterServer>()
                .eq(JMeterServer::getProjectId, projectId));
        projectDatasourceMapper.delete(new LambdaQueryWrapper<ProjectDatasource>()
                .eq(ProjectDatasource::getProjectId, projectId));
        projectMapper.deleteById(projectId);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        for (TestExecution execution : executionsByTask(taskId)) {
            deleteExecution(execution.getId());
        }
        testTaskStepMapper.delete(new LambdaQueryWrapper<TestTaskStep>()
                .eq(TestTaskStep::getTaskId, taskId));
        testTaskMapper.deleteById(taskId);
    }

    @Transactional
    public void deleteExecution(Long executionId) {
        for (TestResult result : resultsByExecution(executionId)) {
            deleteResult(result.getId());
        }
        deleteCleanupRunsByExecution(executionId);
        testExecutionStepMapper.delete(new LambdaQueryWrapper<TestExecutionStep>()
                .eq(TestExecutionStep::getExecutionId, executionId));
        testExecutionMapper.deleteById(executionId);
    }

    @Transactional
    public void deleteResult(Long resultId) {
        deleteReportsByResult(resultId);
        deleteImagesByResult(resultId);
        testResultMetricMapper.delete(new LambdaQueryWrapper<TestResultMetric>()
                .eq(TestResultMetric::getResultId, resultId));
        testResultMapper.deleteById(resultId);
    }

    @Transactional
    public void deleteReport(Long reportId) {
        testReportMapper.deleteById(reportId);
    }

    private void deleteCleanupPlan(Long planId) {
        cleanupPlanSqlMapper.delete(new LambdaQueryWrapper<CleanupPlanSql>()
                .eq(CleanupPlanSql::getCleanupPlanId, planId));
        cleanupPlanMapper.deleteById(planId);
    }

    private void deleteCleanupRunsByExecution(Long executionId) {
        for (CleanupRun run : cleanupRunsByExecution(executionId)) {
            cleanupRunStepMapper.delete(new LambdaQueryWrapper<CleanupRunStep>()
                    .eq(CleanupRunStep::getCleanupRunId, run.getId()));
            cleanupRunMapper.deleteById(run.getId());
        }
    }

    private void deleteReportsByProject(Long projectId) {
        testReportMapper.delete(new LambdaQueryWrapper<TestReport>()
                .eq(TestReport::getProjectId, projectId));
    }

    private void deleteReportsByResult(Long resultId) {
        String id = String.valueOf(resultId);
        testReportMapper.delete(new LambdaQueryWrapper<TestReport>()
                .eq(TestReport::getResultIdsJson, "[" + id + "]")
                .or(wrapper -> wrapper.likeRight(TestReport::getResultIdsJson, "[" + id + ","))
                .or(wrapper -> wrapper.like(TestReport::getResultIdsJson, "," + id + ","))
                .or(wrapper -> wrapper.likeLeft(TestReport::getResultIdsJson, "," + id + "]")));
    }

    private void deleteImagesByResult(Long resultId) {
        List<TestResultImage> images = testResultImageMapper.selectList(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId));
        Path root = Path.of(imageDir).toAbsolutePath().normalize();
        for (TestResultImage image : images) {
            Path path = root.resolve(image.getFilePath()).normalize();
            if (path.startsWith(root)) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        }
        testResultImageMapper.delete(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId));
    }

    private List<TestTask> tasksByProject(Long projectId) {
        return testTaskMapper.selectList(new LambdaQueryWrapper<TestTask>()
                .eq(TestTask::getProjectId, projectId));
    }

    private List<TestExecution> executionsByProject(Long projectId) {
        return testExecutionMapper.selectList(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getProjectId, projectId));
    }

    private List<TestExecution> executionsByTask(Long taskId) {
        return testExecutionMapper.selectList(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getTaskId, taskId));
    }

    private List<TestResult> resultsByProject(Long projectId) {
        return testResultMapper.selectList(new LambdaQueryWrapper<TestResult>()
                .eq(TestResult::getProjectId, projectId));
    }

    private List<TestResult> resultsByExecution(Long executionId) {
        return testResultMapper.selectList(new LambdaQueryWrapper<TestResult>()
                .eq(TestResult::getExecutionId, executionId));
    }

    private List<CleanupPlan> cleanupPlansByProject(Long projectId) {
        return cleanupPlanMapper.selectList(new LambdaQueryWrapper<CleanupPlan>()
                .eq(CleanupPlan::getProjectId, projectId));
    }

    private List<CleanupRun> cleanupRunsByExecution(Long executionId) {
        return cleanupRunMapper.selectList(new LambdaQueryWrapper<CleanupRun>()
                .eq(CleanupRun::getExecutionId, executionId));
    }
}
