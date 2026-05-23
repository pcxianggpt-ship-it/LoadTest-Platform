package com.loadtest.platform.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ExecutionSchedulingTest {

    @Test
    void schedulerChecksForDueExecutionsEverySecond() throws Exception {
        assertThat(fixedDelay("promoteScheduledExecutions")).isEqualTo(1000);
        assertThat(fixedDelay("runOnePendingExecution")).isEqualTo(1000);
    }

    @Test
    void dueCheckComparesActualInstantsAcrossTimeZones() {
        assertThat(ExecutionService.isDue(
                "2026-05-23T02:00:00Z",
                "2026-05-23T10:00:00+08:00"
        )).isTrue();
        assertThat(ExecutionService.isDue(
                "2026-05-23T02:00:01Z",
                "2026-05-23T10:00:00+08:00"
        )).isFalse();
    }

    private long fixedDelay(String methodName) throws Exception {
        Method method = ExecutionScheduler.class.getMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        return scheduled.fixedDelay();
    }
}
