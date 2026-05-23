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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultInfluxMetricClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> queryString = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/query", this::handleQuery);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void queriesJMeterBackendListenerFieldsAndMapsJMeterMetrics() {
        ProjectDatasource datasource = datasource();
        DefaultInfluxMetricClient client = new DefaultInfluxMetricClient();

        List<MetricSample> metrics = client.queryJMeterSummary(
                datasource,
                "2026-05-13T10:00:00+08:00",
                "2026-05-13T10:10:00+08:00"
        );

        assertThat(queryString.get()).contains("db=jmeter");
        assertThat(decodedQuery()).contains("jmeter_summary")
                .contains("2026-05-13T02:00:00Z")
                .contains("2026-05-13T02:10:00Z")
                .contains("mean(hit) AS avg_tps")
                .contains("max(hit) AS max_tps")
                .contains("mean(avg) AS avg_art")
                .contains("mean(\"pct95.0\") AS p95_art")
                .contains("sum(count) AS requests")
                .contains("sum(countError) AS failed_requests")
                .contains("transaction = 'all'")
                .contains("statut = 'all'");
        assertThat(metrics).hasSize(9);
        assertThat(metric(metrics, "TPS", "avg").getValue()).isEqualByComparingTo(BigDecimal.valueOf(120.5));
        assertThat(metric(metrics, "TPS", "max").getValue()).isEqualByComparingTo(BigDecimal.valueOf(160));
        assertThat(metric(metrics, "ART", "p95").getValue()).isEqualByComparingTo(BigDecimal.valueOf(980));
        assertThat(metric(metrics, "error_rate", "avg").getValue()).isEqualByComparingTo(BigDecimal.valueOf(0.25));
        assertThat(metric(metrics, "requests", "sum").getUnit()).isEqualTo("count");
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        queryString.set(exchange.getRequestURI().getRawQuery());
        String body = """
                {
                  "results": [
                    {
                      "series": [
                        {
                          "name": "jmeter_summary",
                          "columns": [
                            "time",
                            "avg_tps",
                            "max_tps",
                            "avg_art",
                            "p90_art",
                            "p95_art",
                            "p99_art",
                            "error_rate",
                            "requests",
                            "failed_requests"
                          ],
                          "values": [
                            [
                              "2026-05-13T10:10:00Z",
                              120.5,
                              160,
                              730,
                              900,
                              980,
                              1300,
                              0.25,
                              72000,
                              180
                            ]
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        respond(exchange, body);
    }

    private ProjectDatasource datasource() {
        ProjectDatasource datasource = new ProjectDatasource();
        datasource.setBaseUrl(baseUrl);
        datasource.setDatabaseName("jmeter");
        datasource.setExtraConfigJson("{\"measurement\":\"jmeter_summary\"}");
        return datasource;
    }

    private String decodedQuery() {
        Map<String, String> params = TestHttp.queryParams(queryString.get());
        return URLDecoder.decode(params.get("q"), StandardCharsets.UTF_8);
    }

    private MetricSample metric(List<MetricSample> metrics, String name, String statType) {
        return metrics.stream()
                .filter(metric -> name.equals(metric.getMetricName()) && statType.equals(metric.getStatType()))
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
