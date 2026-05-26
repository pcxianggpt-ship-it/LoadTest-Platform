package com.loadtest.platform.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionScheduler {

    private final ExecutionService executionService;

    @Scheduled(fixedDelay = 1000)
    public void promoteScheduledExecutions() {
        executionService.promoteDueScheduledExecutions();
    }

    @Scheduled(fixedDelay = 1000)
    public void runOnePendingExecution() {
        executionService.runOnePendingExecution();
    }

    @Scheduled(fixedDelay = 5000)
    public void checkRunningExecutions() {
        executionService.checkRunningExecutions();
    }
}
