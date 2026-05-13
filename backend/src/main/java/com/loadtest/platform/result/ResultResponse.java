package com.loadtest.platform.result;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultResponse {

    private Long id;
    private Long projectId;
    private Long executionId;
    private String name;
    private String status;
    private String timeRangeStart;
    private String timeRangeEnd;
    private String summaryJson;
    private String analysisJson;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private List<ResultMetricResponse> metrics;

    public static ResultResponse from(TestResult result, List<TestResultMetric> metrics) {
        return ResultResponse.builder()
                .id(result.getId())
                .projectId(result.getProjectId())
                .executionId(result.getExecutionId())
                .name(result.getName())
                .status(result.getStatus())
                .timeRangeStart(result.getTimeRangeStart())
                .timeRangeEnd(result.getTimeRangeEnd())
                .summaryJson(result.getSummaryJson())
                .analysisJson(result.getAnalysisJson())
                .createdBy(result.getCreatedBy())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .metrics(metrics.stream().map(ResultMetricResponse::from).toList())
                .build();
    }
}
