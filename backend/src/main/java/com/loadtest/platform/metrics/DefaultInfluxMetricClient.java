package com.loadtest.platform.metrics;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultInfluxMetricClient implements InfluxMetricClient {

    @Override
    public List<MetricSample> queryJMeterSummary(
            ProjectDatasource datasource,
            String startTime,
            String endTime
    ) {
        throw new UnsupportedOperationException("InfluxDB query is not configured yet");
    }
}
