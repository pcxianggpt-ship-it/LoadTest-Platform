package com.loadtest.platform.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPrometheusMetricClientTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> queries = new ArrayList<>();
    private final List<String> queryTimes = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/v1/query", this::handleQuery);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void queriesPrometheusSummaryForEachInstanceAndMapsResourceMetrics() {
        ProjectDatasource datasource = new ProjectDatasource();
        datasource.setBaseUrl(baseUrl);
        DefaultPrometheusMetricClient client = new DefaultPrometheusMetricClient();

        List<MetricSample> metrics = client.queryServerResourceSummary(
                datasource,
                List.of("10.0.0.11:9100"),
                "2026-05-13T10:00:00+08:00",
                "2026-05-13T10:10:00+08:00"
        );

        assertThat(queries).hasSize(7);
        assertThat(queryTimes).containsOnly("2026-05-13T02:10:00Z");
        assertThat(queries).anySatisfy(query -> assertThat(query).contains("node_cpu_seconds_total").contains("10.0.0.11:9100"));
        assertThat(queries).anySatisfy(query -> assertThat(query)
                .contains("node_disk_io_time_seconds_total")
                .contains("10.0.0.11:9100"));
        assertThat(metrics).hasSize(7);
        assertThat(metric(metrics, "cpu", "CPU usage").getValue()).isEqualByComparingTo(BigDecimal.valueOf(82.5));
        assertThat(metric(metrics, "disk_io", "IO wait").getValue()).isEqualByComparingTo(BigDecimal.valueOf(12.7));
        assertThat(metric(metrics, "memory", "Memory usage").getUnit()).isEqualTo("%");
        assertThat(metric(metrics, "network", "Network receive").getUnit()).isEqualTo("B/s");
        assertThat(metric(metrics, "load", "System load").getTargetName()).isEqualTo("10.0.0.11:9100");
    }

    @Test
    void keepsAvailableMetricsWhenOnePrometheusQueryReturnsNoData() {
        ProjectDatasource datasource = new ProjectDatasource();
        datasource.setBaseUrl(baseUrl);
        DefaultPrometheusMetricClient client = new DefaultPrometheusMetricClient();

        List<MetricSample> metrics = client.queryServerResourceSummary(
                datasource,
                List.of("10.0.0.12:9100"),
                "2026-05-13T10:00:00+08:00",
                "2026-05-13T10:10:00+08:00"
        );

        assertThat(queries).hasSize(7);
        assertThat(metrics).hasSize(6);
        assertThat(metrics)
                .noneMatch(metric -> "disk_io".equals(metric.getMetricCategory()) && "IO wait".equals(metric.getMetricName()));
        assertThat(metric(metrics, "cpu", "CPU usage").getValue()).isEqualByComparingTo(BigDecimal.valueOf(82.5));
    }

    @Test
    void queriesAverageK8sPodCpuAndMemoryForEachSelector() {
        ProjectDatasource datasource = new ProjectDatasource();
        datasource.setBaseUrl(baseUrl);
        DefaultPrometheusMetricClient client = new DefaultPrometheusMetricClient();

        List<MetricSample> metrics = client.queryK8sPodResourceAverage(
                datasource,
                List.of(new K8sPodSelector("default", "order-service-.*")),
                "2026-05-13T10:00:00+08:00",
                "2026-05-13T10:10:00+08:00"
        );

        assertThat(queries).hasSize(2);
        assertThat(queryTimes).containsOnly("2026-05-13T02:10:00Z");
        assertThat(queries).anySatisfy(query -> assertThat(query)
                .contains("container_cpu_usage_seconds_total")
                .contains("namespace=\"default\"")
                .contains("pod=~\"order-service-.*\"")
                .contains("[600s:]"));
        assertThat(queries).anySatisfy(query -> assertThat(query)
                .contains("container_memory_working_set_bytes")
                .contains("namespace=\"default\"")
                .contains("pod=~\"order-service-.*\"")
                .contains("[600s:]"));
        assertThat(metrics).hasSize(4);
        assertThat(metric(metrics, "k8s_pod_cpu", "Pod CPU usage", "default/order-service-abc").getStatType())
                .isEqualTo("avg");
        assertThat(metric(metrics, "k8s_pod_cpu", "Pod CPU usage", "default/order-service-abc").getUnit())
                .isEqualTo("cores");
        assertThat(metric(metrics, "k8s_pod_cpu", "Pod CPU usage", "default/order-service-abc").getValue())
                .isEqualByComparingTo("0.35");
        assertThat(metric(metrics, "k8s_pod_cpu", "Pod CPU usage", "default/order-service-def").getValue())
                .isEqualByComparingTo("0.42");
        assertThat(metric(metrics, "k8s_pod_memory", "Pod memory usage", "default/order-service-abc").getUnit())
                .isEqualTo("MiB");
        assertThat(metric(metrics, "k8s_pod_memory", "Pod memory usage", "default/order-service-abc").getValue())
                .isEqualByComparingTo("256");
        assertThat(metric(metrics, "k8s_pod_memory", "Pod memory usage", "default/order-service-def").getValue())
                .isEqualByComparingTo("384");
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        Map<String, String> params = TestHttp.queryParams(exchange.getRequestURI().getRawQuery());
        String query = URLDecoder.decode(params.get("query"), StandardCharsets.UTF_8);
        queries.add(query);
        queryTimes.add(URLDecoder.decode(params.getOrDefault("time", ""), StandardCharsets.UTF_8));
        BigDecimal value = valueForQuery(query);
        if (query.contains("10.0.0.12:9100") && query.contains("node_disk_io_time_seconds_total")) {
            respond(exchange, """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": []
                      }
                    }
                    """);
            return;
        }
        String body = responseForQuery(query, value);
        respond(exchange, body);
    }

    private String responseForQuery(String query, BigDecimal value) {
        if (query.contains("container_cpu_usage_seconds_total")) {
            return """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {"namespace": "default", "pod": "order-service-abc"},
                            "value": [1778656800, "0.35"]
                          },
                          {
                            "metric": {"namespace": "default", "pod": "order-service-def"},
                            "value": [1778656800, "0.42"]
                          }
                        ]
                      }
                    }
                    """;
        }
        if (query.contains("container_memory_working_set_bytes")) {
            return """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {"namespace": "default", "pod": "order-service-abc"},
                            "value": [1778656800, "256"]
                          },
                          {
                            "metric": {"namespace": "default", "pod": "order-service-def"},
                            "value": [1778656800, "384"]
                          }
                        ]
                      }
                    }
                    """;
        }
        return """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": {},
                        "value": [1778656800, "%s"]
                      }
                    ]
                  }
                }
                """.formatted(value.toPlainString());
    }

    private BigDecimal valueForQuery(String query) {
        if (query.contains("node_cpu_seconds_total")) {
            return BigDecimal.valueOf(82.5);
        }
        if (query.contains("container_cpu_usage_seconds_total")) {
            return BigDecimal.valueOf(0.35);
        }
        if (query.contains("container_memory_working_set_bytes")) {
            return BigDecimal.valueOf(256);
        }
        if (query.contains("MemAvailable_bytes")) {
            return BigDecimal.valueOf(71.2);
        }
        if (query.contains("node_filesystem")) {
            return BigDecimal.valueOf(63.4);
        }
        if (query.contains("node_disk_io_time_seconds_total")) {
            return BigDecimal.valueOf(12.7);
        }
        if (query.contains("receive_bytes")) {
            return BigDecimal.valueOf(2048);
        }
        if (query.contains("transmit_bytes")) {
            return BigDecimal.valueOf(4096);
        }
        return BigDecimal.valueOf(2.5);
    }

    private MetricSample metric(List<MetricSample> metrics, String category, String name) {
        return metrics.stream()
                .filter(metric -> category.equals(metric.getMetricCategory()) && name.equals(metric.getMetricName()))
                .findFirst()
                .orElseThrow();
    }

    private MetricSample metric(List<MetricSample> metrics, String category, String name, String targetName) {
        return metrics.stream()
                .filter(metric -> category.equals(metric.getMetricCategory())
                        && name.equals(metric.getMetricName())
                        && targetName.equals(metric.getTargetName()))
                .findFirst()
                .orElseThrow();
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
