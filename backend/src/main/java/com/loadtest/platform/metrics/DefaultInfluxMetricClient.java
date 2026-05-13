package com.loadtest.platform.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class DefaultInfluxMetricClient implements InfluxMetricClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DefaultInfluxMetricClient() {
        this(new RestTemplate(), new ObjectMapper());
    }

    DefaultInfluxMetricClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MetricSample> queryJMeterSummary(
            ProjectDatasource datasource,
            String startTime,
            String endTime
    ) {
        String measurement = measurement(datasource);
        String query = """
                SELECT mean(tps) AS avg_tps,
                       max(tps) AS max_tps,
                       mean(art) AS avg_art,
                       percentile(art, 90) AS p90_art,
                       percentile(art, 95) AS p95_art,
                       percentile(art, 99) AS p99_art,
                       mean(error_rate) AS error_rate,
                       sum(requests) AS requests,
                       sum(failed_requests) AS failed_requests
                FROM "%s"
                WHERE time >= '%s' AND time <= '%s'
                """.formatted(measurement, influxTime(startTime), influxTime(endTime));
        URI uri = UriComponentsBuilder.fromHttpUrl(datasource.getBaseUrl())
                .path("/query")
                .queryParam("db", UriUtils.encodeQueryParam(datasource.getDatabaseName(), java.nio.charset.StandardCharsets.UTF_8))
                .queryParam("q", UriUtils.encodeQueryParam(query, java.nio.charset.StandardCharsets.UTF_8))
                .build(true)
                .toUri();

        JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
        JsonNode series = firstSeries(response);
        Map<String, Integer> columns = columns(series.get("columns"));
        JsonNode values = series.path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("InfluxDB returned no JMeter metrics");
        }
        JsonNode row = values.get(0);

        List<MetricSample> metrics = new ArrayList<>();
        metrics.add(sample("TPS", "avg", value(row, columns, "avg_tps"), "req/s"));
        metrics.add(sample("TPS", "max", value(row, columns, "max_tps"), "req/s"));
        metrics.add(sample("ART", "avg", value(row, columns, "avg_art"), "ms"));
        metrics.add(sample("ART", "p90", value(row, columns, "p90_art"), "ms"));
        metrics.add(sample("ART", "p95", value(row, columns, "p95_art"), "ms"));
        metrics.add(sample("ART", "p99", value(row, columns, "p99_art"), "ms"));
        metrics.add(sample("error_rate", "avg", value(row, columns, "error_rate"), "%"));
        metrics.add(sample("requests", "sum", value(row, columns, "requests"), "count"));
        metrics.add(sample("failed_requests", "sum", value(row, columns, "failed_requests"), "count"));
        return metrics;
    }

    private String measurement(ProjectDatasource datasource) {
        if (datasource.getExtraConfigJson() == null || datasource.getExtraConfigJson().isBlank()) {
            return "jmeter";
        }
        try {
            JsonNode root = objectMapper.readTree(datasource.getExtraConfigJson());
            String measurement = root.path("measurement").asText();
            return measurement == null || measurement.isBlank() ? "jmeter" : measurement;
        } catch (Exception exception) {
            return "jmeter";
        }
    }

    private String influxTime(String time) {
        return OffsetDateTime.parse(time).toInstant().toString();
    }

    private JsonNode firstSeries(JsonNode response) {
        JsonNode series = response.path("results").path(0).path("series").path(0);
        if (series.isMissingNode()) {
            throw new IllegalStateException("InfluxDB returned no series");
        }
        return series;
    }

    private Map<String, Integer> columns(JsonNode columns) {
        java.util.HashMap<String, Integer> indexes = new java.util.HashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            indexes.put(columns.get(index).asText(), index);
        }
        return indexes;
    }

    private BigDecimal value(JsonNode row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || row.get(index) == null || row.get(index).isNull()) {
            return BigDecimal.ZERO;
        }
        return row.get(index).decimalValue();
    }

    private MetricSample sample(String metricName, String statType, BigDecimal value, String unit) {
        return MetricSample.builder()
                .source("influxdb")
                .metricCategory("jmeter")
                .metricName(metricName)
                .targetName("all")
                .statType(statType)
                .value(value)
                .unit(unit)
                .build();
    }
}
