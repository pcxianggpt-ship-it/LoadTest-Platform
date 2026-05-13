package com.loadtest.platform.result;

import java.util.List;

public record AnalysisSummary(
        String overallStatus,
        List<String> riskItems,
        String summary,
        String suggestions
) {
}
