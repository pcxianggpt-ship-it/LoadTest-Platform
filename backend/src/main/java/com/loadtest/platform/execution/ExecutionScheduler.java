package com.loadtest.platform.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionScheduler {

    private final ExecutionService executionService;

    @Scheduled(fixedDelay = 10000)
    public void promoteScheduledExecutions() {
        executionService.promoteDueScheduledExecutions();
    }

    @Scheduled(fixedDelay = 10000)
    public void runOnePendingExecution() {
        executionService.runOnePendingExecution();
    }
}
