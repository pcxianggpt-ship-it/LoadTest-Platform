package com.loadtest.platform.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportResponse {

    private Long id;
    private Long projectId;
    private String title;
    private String reportType;
    private String status;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
    private String contentMarkdown;
    private String contentHtml;
    private String resultIdsJson;
    private String createdBy;
    private String createdAt;
    private String updatedAt;

    public static ReportResponse from(TestReport report) {
        return ReportResponse.builder()
                .id(report.getId())
                .projectId(report.getProjectId())
                .title(report.getTitle())
                .reportType(report.getReportType())
                .status(report.getStatus())
                .errorMessage(report.getErrorMessage())
                .startedAt(report.getStartedAt())
                .finishedAt(report.getFinishedAt())
                .contentMarkdown(report.getContentMarkdown())
                .contentHtml(report.getContentHtml())
                .resultIdsJson(report.getResultIdsJson())
                .createdBy(report.getCreatedBy())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
