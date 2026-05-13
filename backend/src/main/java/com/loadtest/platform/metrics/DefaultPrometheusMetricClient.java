package com.loadtest.platform.metrics;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultPrometheusMetricClient implements PrometheusMetricClient {

    @Override
    public List<MetricSample> queryServerResourceSummary(
            ProjectDatasource datasource,
            List<String> instances,
            String startTime,
            String endTime
    ) {
        throw new UnsupportedOperationException("Prometheus query is not configured yet");
    }
}
