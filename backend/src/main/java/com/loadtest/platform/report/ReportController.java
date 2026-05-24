package com.loadtest.platform.report;

import com.loadtest.platform.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/results/{resultId}/reports")
    public ApiResponse<ReportResponse> generateReport(@PathVariable Long resultId) {
        return ApiResponse.ok(reportService.generateReport(resultId));
    }

    @GetMapping("/api/projects/{projectId}/reports")
    public ApiResponse<List<ReportResponse>> listReports(@PathVariable Long projectId) {
        return ApiResponse.ok(reportService.listReports(projectId));
    }

    @GetMapping("/api/reports/{reportId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable Long reportId) {
        return ApiResponse.ok(reportService.getReport(reportId));
    }

    @DeleteMapping("/api/reports/{reportId}")
    public ApiResponse<Void> deleteReport(@PathVariable Long reportId) {
        reportService.deleteReport(reportId);
        return ApiResponse.ok(null);
    }
}
