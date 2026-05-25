package com.loadtest.platform.metrics;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.util.List;

public interface PrometheusMetricClient {

    List<MetricSample> queryServerResourceSummary(
            ProjectDatasource datasource,
            List<String> instances,
            String startTime,
            String endTime
    );

    List<MetricSample> queryK8sPodResourceAverage(
            ProjectDatasource datasource,
            List<K8sPodSelector> selectors,
            String startTime,
            String endTime
    );
}
