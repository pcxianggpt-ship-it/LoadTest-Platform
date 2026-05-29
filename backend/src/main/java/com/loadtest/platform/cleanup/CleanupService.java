package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.execution.TestExecution;
import com.loadtest.platform.execution.TestExecutionMapper;
import com.loadtest.platform.project.ProjectMapper;
import com.loadtest.platform.task.TestTask;
import com.loadtest.platform.task.TestTaskMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private static final List<String> DATABASE_TYPES = List.of("mysql", "oracle");

    private final ProjectMapper projectMapper;
    private final TestTaskMapper testTaskMapper;
    private final TestExecutionMapper testExecutionMapper;
    private final BusinessDatabaseMapper businessDatabaseMapper;
    private final CleanupPlanMapper cleanupPlanMapper;
    private final CleanupPlanSqlMapper cleanupPlanSqlMapper;
    private final CleanupRunMapper cleanupRunMapper;
    private final CleanupRunStepMapper cleanupRunStepMapper;
    private final CleanupSqlExecutor cleanupSqlExecutor;

    @Transactional
    public BusinessDatabaseResponse createBusinessDatabase(Long projectId, BusinessDatabaseRequest request) {
        ensureProjectExists(projectId);
        validateBusinessDatabaseRequest(request);
        String now = OffsetDateTime.now().toString();
        BusinessDatabase database = new BusinessDatabase();
        database.setProjectId(projectId);
        apply(database, request);
        database.setCreatedAt(now);
        database.setUpdatedAt(now);
        businessDatabaseMapper.insert(database);
        return BusinessDatabaseResponse.from(database);
    }

    public List<BusinessDatabaseResponse> listBusinessDatabases(Long projectId) {
        ensureProjectExists(projectId);
        return businessDatabaseMapper.selectList(new LambdaQueryWrapper<BusinessDatabase>()
                        .eq(BusinessDatabase::getProjectId, projectId)
                        .orderByAsc(BusinessDatabase::getId))
                .stream()
                .map(BusinessDatabaseResponse::from)
                .toList();
    }

    @Transactional
    public BusinessDatabaseResponse updateBusinessDatabase(Long projectId, Long databaseId, BusinessDatabaseRequest request) {
        ensureProjectExists(projectId);
        validateBusinessDatabaseRequest(request);
        BusinessDatabase database = getBusinessDatabase(projectId, databaseId);
        apply(database, request);
        database.setUpdatedAt(OffsetDateTime.now().toString());
        businessDatabaseMapper.updateById(database);
        return BusinessDatabaseResponse.from(database);
    }

    @Transactional
    public void deleteBusinessDatabase(Long projectId, Long databaseId) {
        getBusinessDatabase(projectId, databaseId);
        businessDatabaseMapper.deleteById(databaseId);
    }

    @Transactional
    public CleanupPlanResponse createCleanupPlan(Long projectId, CleanupPlanRequest request) {
        ensureProjectExists(projectId);
        getBusinessDatabase(projectId, request.getBusinessDatabaseId());
        String now = OffsetDateTime.now().toString();
        CleanupPlan plan = new CleanupPlan();
        plan.setProjectId(projectId);
        apply(plan, request);
        plan.setStatus("active");
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        cleanupPlanMapper.insert(plan);
        replaceSqlStatements(plan.getId(), request.getSqlStatements(), now);
        return CleanupPlanResponse.from(plan, listSqls(plan.getId()));
    }

    public List<CleanupPlanResponse> listCleanupPlans(Long projectId) {
        ensureProjectExists(projectId);
        return cleanupPlanMapper.selectList(new LambdaQueryWrapper<CleanupPlan>()
                        .eq(CleanupPlan::getProjectId, projectId)
                        .orderByDesc(CleanupPlan::getId))
                .stream()
                .map(plan -> CleanupPlanResponse.from(plan, listSqls(plan.getId())))
                .toList();
    }

    @Transactional
    public CleanupPlanResponse updateCleanupPlan(Long projectId, Long planId, CleanupPlanRequest request) {
        ensureProjectExists(projectId);
        getBusinessDatabase(projectId, request.getBusinessDatabaseId());
        CleanupPlan plan = getCleanupPlan(projectId, planId);
        String now = OffsetDateTime.now().toString();
        apply(plan, request);
        plan.setUpdatedAt(now);
        cleanupPlanMapper.updateById(plan);
        cleanupPlanSqlMapper.delete(new LambdaQueryWrapper<CleanupPlanSql>()
                .eq(CleanupPlanSql::getCleanupPlanId, planId));
        replaceSqlStatements(planId, request.getSqlStatements(), now);
        return CleanupPlanResponse.from(plan, listSqls(planId));
    }

    @Transactional
    public void deleteCleanupPlan(Long projectId, Long planId) {
        getCleanupPlan(projectId, planId);
        cleanupPlanSqlMapper.delete(new LambdaQueryWrapper<CleanupPlanSql>()
                .eq(CleanupPlanSql::getCleanupPlanId, planId));
        cleanupPlanMapper.deleteById(planId);
    }

    @Transactional
    public void runAfterExecution(Long executionId) {
        TestExecution execution = getExecution(executionId);
        runCleanup(execution, false);
    }

    @Transactional
    public TestExecution retryCleanup(Long executionId) {
        TestExecution execution = getExecution(executionId);
        if (!"success".equals(execution.getStatus())) {
            throw new IllegalArgumentException("only success execution can retry cleanup");
        }
        return runCleanup(execution, true);
    }

    private TestExecution runCleanup(TestExecution execution, boolean retry) {
        TestTask task = testTaskMapper.selectById(execution.getTaskId());
        if (task == null || task.getCleanupPlanId() == null) {
            markSkipped(execution);
            return execution;
        }
        CleanupPlan plan = cleanupPlanMapper.selectById(task.getCleanupPlanId());
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            markSkipped(execution);
            return execution;
        }
        BusinessDatabase database = businessDatabaseMapper.selectById(plan.getBusinessDatabaseId());
        if (database == null || !"active".equals(database.getStatus())) {
            markFailedWithoutRun(execution, "business database not available");
            return execution;
        }

        String now = OffsetDateTime.now().toString();
        CleanupRun run = new CleanupRun();
        run.setExecutionId(execution.getId());
        run.setCleanupPlanId(plan.getId());
        run.setBusinessDatabaseId(database.getId());
        run.setStatus("running");
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        cleanupRunMapper.insert(run);

        execution.setCleanupStatus("running");
        execution.setCleanupStartedAt(now);
        execution.setCleanupEndedAt(null);
        execution.setCleanupErrorMessage(null);
        execution.setUpdatedAt(now);
        testExecutionMapper.updateById(execution);

        List<CleanupPlanSql> sqls = listSqls(plan.getId());
        for (CleanupPlanSql sql : sqls) {
            CleanupRunStep step = createRunStep(run.getId(), sql);
            try {
                int affectedRows = cleanupSqlExecutor.execute(database, sql.getSqlText());
                finishRunStep(step, "success", affectedRows, null);
            } catch (Exception exception) {
                finishRunStep(step, "failed", null, exception.getMessage());
                finishRun(run, execution, "failed", exception.getMessage());
                return execution;
            }
        }
        finishRun(run, execution, "success", null);
        return execution;
    }

    private void apply(BusinessDatabase database, BusinessDatabaseRequest request) {
        database.setName(request.getName());
        database.setDatabaseType(request.getDatabaseType());
        database.setJdbcUrl(request.getJdbcUrl());
        database.setUsername(request.getUsername());
        database.setPasswordEncrypted(request.getPasswordEncrypted());
        database.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? "active"
                : request.getStatus());
    }

    private void apply(CleanupPlan plan, CleanupPlanRequest request) {
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setBusinessDatabaseId(request.getBusinessDatabaseId());
        plan.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
    }

    private void replaceSqlStatements(Long planId, List<String> statements, String now) {
        int order = 1;
        for (String statement : statements) {
            CleanupPlanSql sql = new CleanupPlanSql();
            sql.setCleanupPlanId(planId);
            sql.setStepOrder(order++);
            sql.setSqlText(statement);
            sql.setEnabled(true);
            sql.setCreatedAt(now);
            sql.setUpdatedAt(now);
            cleanupPlanSqlMapper.insert(sql);
        }
    }

    private List<CleanupPlanSql> listSqls(Long planId) {
        return cleanupPlanSqlMapper.selectList(new LambdaQueryWrapper<CleanupPlanSql>()
                .eq(CleanupPlanSql::getCleanupPlanId, planId)
                .eq(CleanupPlanSql::getEnabled, true)
                .orderByAsc(CleanupPlanSql::getStepOrder));
    }

    private CleanupRunStep createRunStep(Long runId, CleanupPlanSql sql) {
        String now = OffsetDateTime.now().toString();
        CleanupRunStep step = new CleanupRunStep();
        step.setCleanupRunId(runId);
        step.setStepOrder(sql.getStepOrder());
        step.setSqlText(sql.getSqlText());
        step.setStatus("running");
        step.setStartedAt(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        cleanupRunStepMapper.insert(step);
        return step;
    }

    private void finishRunStep(CleanupRunStep step, String status, Integer affectedRows, String errorMessage) {
        String now = OffsetDateTime.now().toString();
        step.setStatus(status);
        step.setAffectedRows(affectedRows);
        step.setErrorMessage(errorMessage);
        step.setEndedAt(now);
        step.setUpdatedAt(now);
        cleanupRunStepMapper.updateById(step);
    }

    private void finishRun(CleanupRun run, TestExecution execution, String status, String errorMessage) {
        String now = OffsetDateTime.now().toString();
        run.setStatus(status);
        run.setEndedAt(now);
        run.setErrorMessage(errorMessage);
        run.setUpdatedAt(now);
        cleanupRunMapper.updateById(run);

        execution.setCleanupStatus(status);
        execution.setCleanupEndedAt(now);
        execution.setCleanupErrorMessage(errorMessage);
        execution.setUpdatedAt(now);
        testExecutionMapper.updateById(execution);
    }

    private void markSkipped(TestExecution execution) {
        String now = OffsetDateTime.now().toString();
        execution.setCleanupStatus("skipped");
        execution.setCleanupStartedAt(now);
        execution.setCleanupEndedAt(now);
        execution.setCleanupErrorMessage(null);
        execution.setUpdatedAt(now);
        testExecutionMapper.updateById(execution);
    }

    private void markFailedWithoutRun(TestExecution execution, String errorMessage) {
        String now = OffsetDateTime.now().toString();
        execution.setCleanupStatus("failed");
        execution.setCleanupStartedAt(now);
        execution.setCleanupEndedAt(now);
        execution.setCleanupErrorMessage(errorMessage);
        execution.setUpdatedAt(now);
        testExecutionMapper.updateById(execution);
    }

    private BusinessDatabase getBusinessDatabase(Long projectId, Long databaseId) {
        BusinessDatabase database = businessDatabaseMapper.selectOne(new LambdaQueryWrapper<BusinessDatabase>()
                .eq(BusinessDatabase::getProjectId, projectId)
                .eq(BusinessDatabase::getId, databaseId)
                .last("limit 1"));
        if (database == null) {
            throw new NotFoundException("business database not found");
        }
        return database;
    }

    private CleanupPlan getCleanupPlan(Long projectId, Long planId) {
        CleanupPlan plan = cleanupPlanMapper.selectOne(new LambdaQueryWrapper<CleanupPlan>()
                .eq(CleanupPlan::getProjectId, projectId)
                .eq(CleanupPlan::getId, planId)
                .last("limit 1"));
        if (plan == null) {
            throw new NotFoundException("cleanup plan not found");
        }
        return plan;
    }

    private TestExecution getExecution(Long executionId) {
        TestExecution execution = testExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw new NotFoundException("execution not found");
        }
        return execution;
    }

    private void ensureProjectExists(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new NotFoundException("project not found");
        }
    }

    private void validateBusinessDatabaseRequest(BusinessDatabaseRequest request) {
        if (!DATABASE_TYPES.contains(request.getDatabaseType())) {
            throw new IllegalArgumentException("unsupported business database type");
        }
    }
}
