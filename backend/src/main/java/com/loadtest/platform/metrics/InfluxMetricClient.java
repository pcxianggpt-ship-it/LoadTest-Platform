package com.loadtest.platform.metrics;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.util.List;

public interface InfluxMetricClient {

    List<MetricSample> queryJMeterSummary(
            ProjectDatasource datasource,
            String startTime,
            String endTime
    );
}
