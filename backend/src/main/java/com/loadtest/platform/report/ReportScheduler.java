package com.loadtest.platform.report;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "loadtest.report.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReportScheduler {

    private final ReportService reportService;

    @Scheduled(fixedDelay = 3000, initialDelay = 3000)
    public void runOnePendingReport() {
        reportService.runOnePendingReport();
    }
}
