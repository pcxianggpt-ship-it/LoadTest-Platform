package com.loadtest.platform.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DefaultPrometheusMetricClient implements PrometheusMetricClient {

    private final RestTemplate restTemplate;

    public DefaultPrometheusMetricClient() {
        this(new RestTemplate());
    }

    DefaultPrometheusMetricClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<MetricSample> queryServerResourceSummary(
            ProjectDatasource datasource,
            List<String> instances,
            String startTime,
            String endTime
    ) {
        List<MetricSample> metrics = new ArrayList<>();
        String range = range(startTime, endTime);
        for (String instance : instances) {
            metrics.add(sample("cpu", "CPU usage", instance, query(datasource, cpuQuery(instance, range)), "%"));
            metrics.add(sample("memory", "Memory usage", instance, query(datasource, memoryQuery(instance, range)), "%"));
            metrics.add(sample("disk", "Disk usage", instance, query(datasource, diskQuery(instance, range)), "%"));
            metrics.add(sample("disk_io", "IO wait", instance, query(datasource, ioWaitQuery(instance, range)), "%"));
            metrics.add(sample("network", "Network receive", instance, query(datasource, networkReceiveQuery(instance, range)), "B/s"));
            metrics.add(sample("network", "Network transmit", instance, query(datasource, networkTransmitQuery(instance, range)), "B/s"));
            metrics.add(sample("load", "System load", instance, query(datasource, loadQuery(instance, range)), ""));
        }
        return metrics;
    }

    private BigDecimal query(ProjectDatasource datasource, String promql) {
        URI uri = UriComponentsBuilder.fromHttpUrl(datasource.getBaseUrl())
                .path("/api/v1/query")
                .queryParam("query", promql)
                .build()
                .encode()
                .toUri();
        JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
        JsonNode value = response.path("data").path("result").path(0).path("value").path(1);
        if (value.isMissingNode()) {
            throw new IllegalStateException("Prometheus returned no data");
        }
        return new BigDecimal(value.asText());
    }

    private String range(String startTime, String endTime) {
        try {
            long seconds = Math.max(1, Duration.between(
                    OffsetDateTime.parse(startTime),
                    OffsetDateTime.parse(endTime)
            ).toSeconds());
            return seconds + "s";
        } catch (Exception exception) {
            return "600s";
        }
    }

    private String cpuQuery(String instance, String range) {
        return "max_over_time((100 - (avg by(instance) (rate(node_cpu_seconds_total{mode=\"idle\",instance=\""
                + instance + "\"}[1m])) * 100))[" + range + ":])";
    }

    private String memoryQuery(String instance, String range) {
        return "max_over_time(((1 - node_memory_MemAvailable_bytes{instance=\""
                + instance + "\"} / node_memory_MemTotal_bytes{instance=\"" + instance + "\"}) * 100)[" + range + ":])";
    }

    private String diskQuery(String instance, String range) {
        return "max_over_time(((1 - node_filesystem_avail_bytes{instance=\""
                + instance + "\",fstype!~\"tmpfs|overlay\"} / node_filesystem_size_bytes{instance=\"" + instance
                + "\",fstype!~\"tmpfs|overlay\"}) * 100)[" + range + ":])";
    }

    private String ioWaitQuery(String instance, String range) {
        return "max_over_time((avg by(instance) (rate(node_cpu_seconds_total{mode=\"iowait\",instance=\""
                + instance + "\"}[1m])) * 100)[" + range + ":])";
    }

    private String networkReceiveQuery(String instance, String range) {
        return "max_over_time(rate(node_network_receive_bytes_total{instance=\""
                + instance + "\",device!~\"lo\"}[1m])[" + range + ":])";
    }

    private String networkTransmitQuery(String instance, String range) {
        return "max_over_time(rate(node_network_transmit_bytes_total{instance=\""
                + instance + "\",device!~\"lo\"}[1m])[" + range + ":])";
    }

    private String loadQuery(String instance, String range) {
        return "max_over_time(node_load1{instance=\"" + instance + "\"}[" + range + ":])";
    }

    private MetricSample sample(String category, String name, String target, BigDecimal value, String unit) {
        return MetricSample.builder()
                .source("prometheus")
                .metricCategory(category)
                .metricName(name)
                .targetName(target)
                .statType("max")
                .value(value)
                .unit(unit)
                .build();
    }
}
