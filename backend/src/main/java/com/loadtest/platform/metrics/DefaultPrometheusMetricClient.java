package com.loadtest.platform.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DefaultPrometheusMetricClient implements PrometheusMetricClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultPrometheusMetricClient.class);

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
        String queryTime = queryTime(endTime);
        for (String instance : instances) {
            addMetric(metrics, datasource, "cpu", "CPU usage", instance, cpuQuery(instance, range), "%", queryTime);
            addMetric(metrics, datasource, "memory", "Memory usage", instance, memoryQuery(instance, range), "%", queryTime);
            addMetric(metrics, datasource, "disk", "Disk usage", instance, diskQuery(instance, range), "%", queryTime);
            addMetric(metrics, datasource, "disk_io", "IO wait", instance, ioWaitQuery(instance, range), "%", queryTime);
            addMetric(metrics, datasource, "network", "Network receive", instance, networkReceiveQuery(instance, range), "B/s", queryTime);
            addMetric(metrics, datasource, "network", "Network transmit", instance, networkTransmitQuery(instance, range), "B/s", queryTime);
            addMetric(metrics, datasource, "load", "System load", instance, loadQuery(instance, range), "", queryTime);
        }
        if (!instances.isEmpty() && metrics.isEmpty()) {
            throw new IllegalStateException("Prometheus returned no resource metrics");
        }
        return metrics;
    }

    @Override
    public List<MetricSample> queryK8sPodResourceAverage(
            ProjectDatasource datasource,
            List<K8sPodSelector> selectors,
            String startTime,
            String endTime
    ) {
        List<MetricSample> metrics = new ArrayList<>();
        String range = range(startTime, endTime);
        String queryTime = queryTime(endTime);
        for (K8sPodSelector selector : selectors) {
            addAverageMetrics(metrics, datasource, "k8s_pod_cpu", "Pod CPU usage",
                    podCpuAverageQuery(selector, range), "cores", queryTime);
            addAverageMetrics(metrics, datasource, "k8s_pod_memory", "Pod memory usage",
                    podMemoryAverageQuery(selector, range), "MiB", queryTime);
        }
        if (!selectors.isEmpty() && metrics.isEmpty()) {
            throw new IllegalStateException("Prometheus returned no k8s pod resource metrics");
        }
        return metrics;
    }

    private void addMetric(
            List<MetricSample> metrics,
            ProjectDatasource datasource,
            String category,
            String name,
            String instance,
            String promql,
            String unit,
            String queryTime
    ) {
        try {
            metrics.add(sample(category, name, instance, query(datasource, promql, queryTime), unit));
        } catch (Exception exception) {
            log.warn(
                    "Prometheus metric query failed: datasourceId={}, baseUrl={}, category={}, name={}, instance={}, queryTime={}, error={}, promql={}",
                    datasource.getId(),
                    datasource.getBaseUrl(),
                    category,
                    name,
                    instance,
                    queryTime,
                    exception.getMessage(),
                    promql
            );
        }
    }

    private void addAverageMetric(
            List<MetricSample> metrics,
            ProjectDatasource datasource,
            String category,
            String name,
            String instance,
            String promql,
            String unit,
            String queryTime
    ) {
        try {
            metrics.add(sample(category, name, instance, query(datasource, promql, queryTime), unit, "avg"));
        } catch (Exception exception) {
            log.warn(
                    "Prometheus metric query failed: datasourceId={}, baseUrl={}, category={}, name={}, instance={}, queryTime={}, error={}, promql={}",
                    datasource.getId(),
                    datasource.getBaseUrl(),
                    category,
                    name,
                    instance,
                    queryTime,
                    exception.getMessage(),
                    promql
            );
        }
    }

    private void addAverageMetrics(
            List<MetricSample> metrics,
            ProjectDatasource datasource,
            String category,
            String name,
            String promql,
            String unit,
            String queryTime
    ) {
        try {
            for (MetricSample sample : samples(category, name, queryResult(datasource, promql, queryTime), unit, "avg")) {
                metrics.add(sample);
            }
        } catch (Exception exception) {
            log.warn(
                    "Prometheus metric query failed: datasourceId={}, baseUrl={}, category={}, name={}, queryTime={}, error={}, promql={}",
                    datasource.getId(),
                    datasource.getBaseUrl(),
                    category,
                    name,
                    queryTime,
                    exception.getMessage(),
                    promql
            );
        }
    }

    private BigDecimal query(ProjectDatasource datasource, String promql, String queryTime) {
        JsonNode result = queryResult(datasource, promql, queryTime);
        JsonNode value = result.path(0).path("value").path(1);
        if (value.isMissingNode()) {
            log.warn("Prometheus returned no data: datasourceId={}, baseUrl={}, queryTime={}, promql={}, result={}", datasource.getId(), datasource.getBaseUrl(), queryTime, promql, result);
            throw new IllegalStateException("Prometheus returned no data");
        }
        return new BigDecimal(value.asText());
    }

    private JsonNode queryResult(ProjectDatasource datasource, String promql, String queryTime) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(datasource.getBaseUrl())
                .path("/api/v1/query")
                .queryParam("query", promql);
        if (queryTime != null) {
            builder.queryParam("time", queryTime);
        }
        URI uri = builder
                .build()
                .encode()
                .toUri();
        log.info("Querying Prometheus metric: datasourceId={}, baseUrl={}, queryTime={}, promql={}", datasource.getId(), datasource.getBaseUrl(), queryTime, promql);
        JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            log.warn("Prometheus returned no data: datasourceId={}, baseUrl={}, queryTime={}, promql={}, response={}", datasource.getId(), datasource.getBaseUrl(), queryTime, promql, response);
            throw new IllegalStateException("Prometheus returned no data");
        }
        return result;
    }

    private String queryTime(String endTime) {
        try {
            return OffsetDateTime.parse(endTime).toInstant().toString();
        } catch (Exception exception) {
            return null;
        }
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

    private String podCpuAverageQuery(K8sPodSelector selector, String range) {
        return "avg_over_time((sum by(namespace,pod) (rate(container_cpu_usage_seconds_total{namespace=\""
                + selector.namespace() + "\",pod=~\"" + selector.podRegex()
                + "\",container!=\"\",image!=\"\"}[1m])))[" + range + ":])";
    }

    private String podMemoryAverageQuery(K8sPodSelector selector, String range) {
        return "avg_over_time((sum by(namespace,pod) (container_memory_working_set_bytes{namespace=\""
                + selector.namespace() + "\",pod=~\"" + selector.podRegex()
                + "\",container!=\"\",image!=\"\"} / 1024 / 1024))[" + range + ":])";
    }

    private MetricSample sample(String category, String name, String target, BigDecimal value, String unit) {
        return sample(category, name, target, value, unit, "max");
    }

    private MetricSample sample(String category, String name, String target, BigDecimal value, String unit, String statType) {
        return MetricSample.builder()
                .source("prometheus")
                .metricCategory(category)
                .metricName(name)
                .targetName(target)
                .statType(statType)
                .value(value)
                .unit(unit)
                .build();
    }

    private List<MetricSample> samples(String category, String name, JsonNode result, String unit, String statType) {
        List<MetricSample> samples = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode value = item.path("value").path(1);
            if (value.isMissingNode()) {
                continue;
            }
            samples.add(sample(
                    category,
                    name,
                    targetName(item.path("metric")),
                    new BigDecimal(value.asText()),
                    unit,
                    statType
            ));
        }
        return samples;
    }

    private String targetName(JsonNode labels) {
        String namespace = labels.path("namespace").asText("");
        String pod = labels.path("pod").asText("");
        if (!namespace.isBlank() && !pod.isBlank()) {
            return namespace + "/" + pod;
        }
        if (!pod.isBlank()) {
            return pod;
        }
        return labels.toString();
    }
}
