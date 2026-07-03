package com.loadtest.platform.report;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportScheduler {

    private final ReportService reportService;

    @Scheduled(fixedDelay = 3000, initialDelay = 3000)
    public void runOnePendingReport() {
        reportService.runOnePendingReport();
    }
}
