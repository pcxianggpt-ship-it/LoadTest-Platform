package com.loadtest.platform.projectconfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectConfigControllerTest {

    private static final Path DB_PATH = Path.of("target", "project-config-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    private Path tempDir;

    @Test
    void upsertsAndReadsJMeterServer() throws Exception {
        Long projectId = createProject();
        String body = """
                {
                  "name": "jmeter-01",
                  "host": "10.0.0.10",
                  "sshPort": 22,
                  "sshUsername": "jmeter",
                  "sshAuthType": "password",
                  "sshPasswordEncrypted": "encrypted-password",
                  "jmeterHome": "/opt/apache-jmeter",
                  "scriptDir": "/opt/jmeter/scripts",
                  "resultDir": "/opt/jmeter/results",
                  "logDir": "/opt/jmeter/logs"
                }
                """;

        mockMvc.perform(put("/api/projects/{projectId}/jmeter-server", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.host").value("10.0.0.10"))
                .andExpect(jsonPath("$.data.sshPort").value(22));

        mockMvc.perform(get("/api/projects/{projectId}/jmeter-server", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("jmeter-01"))
                .andExpect(jsonPath("$.data.scriptDir").value("/opt/jmeter/scripts"));
    }

    @Test
    void upsertsAndListsDatasources() throws Exception {
        Long projectId = createProject();
        String influxBody = """
                {
                  "name": "jmeter-influx",
                  "baseUrl": "http://10.0.0.20:8086",
                  "databaseName": "jmeter",
                  "username": "reader",
                  "passwordEncrypted": "encrypted",
                  "extraConfigJson": "{\\"measurement\\":\\"jmeter\\",\\"elapsed_field\\":\\"avg\\"}"
                }
                """;
        String prometheusBody = """
                {
                  "name": "resource-prometheus",
                  "baseUrl": "http://10.0.0.21:9090",
                  "extraConfigJson": "{\\"instances\\":[\\"10.0.0.11:9100\\",\\"10.0.0.12:9100\\"]}"
                }
                """;

        mockMvc.perform(put("/api/projects/{projectId}/datasources/influxdb", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(influxBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("influxdb"))
                .andExpect(jsonPath("$.data.databaseName").value("jmeter"));

        mockMvc.perform(put("/api/projects/{projectId}/datasources/prometheus", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prometheusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("prometheus"));

        mockMvc.perform(get("/api/projects/{projectId}/datasources", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void rejectsInvalidJMeterServerConfig() throws Exception {
        Long projectId = createProject();
        String body = """
                {
                  "name": "jmeter-01",
                  "host": "10.0.0.10",
                  "sshPort": 70000,
                  "sshUsername": "jmeter",
                  "sshAuthType": "token",
                  "jmeterHome": "/opt/apache-jmeter",
                  "scriptDir": "/opt/jmeter/scripts",
                  "logDir": "/opt/jmeter/logs"
                }
                """;

        mockMvc.perform(put("/api/projects/{projectId}/jmeter-server", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsInvalidDatasourceTypeAndUrl() throws Exception {
        Long projectId = createProject();
        String body = """
                {
                  "name": "bad",
                  "baseUrl": "ftp://10.0.0.21:9090"
                }
                """;

        mockMvc.perform(put("/api/projects/{projectId}/datasources/mysql", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testsMetricQueryEndpointsWithoutSaving() throws Exception {
        Long projectId = createProject();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/prometheus/api/v1/query", request -> {
            boolean queryMatches = "query=vector(1)".equals(request.getRequestURI().getQuery());
            respond(request, queryMatches ? 200 : 400,
                    "{\"status\":\"success\",\"data\":{\"result\":[{\"value\":[1,\"1\"]}]}}");
        });
        server.createContext("/influx/query", request -> {
            boolean databaseMatches = "db=jmeter%26metrics&q=SHOW%20RETENTION%20POLICIES"
                    .equals(request.getRequestURI().getRawQuery());
            respond(request, 200, databaseMatches
                    ? "{\"results\":[{\"statement_id\":0}]}"
                    : "{\"results\":[{\"statement_id\":0,\"error\":\"database not found\"}]}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            testDatasource(projectId, "prometheus", Map.of("baseUrl", baseUrl + "/prometheus/"))
                    .andExpect(jsonPath("$.data.connected").value(true));
            testDatasource(projectId, "influxdb", Map.of("baseUrl", baseUrl + "/influx", "databaseName", "jmeter&metrics"))
                    .andExpect(jsonPath("$.data.connected").value(true));
            testDatasource(projectId, "influxdb", Map.of("baseUrl", baseUrl + "/influx", "databaseName", "missing"))
                    .andExpect(jsonPath("$.data.connected").value(false));
            mockMvc.perform(get("/api/projects/{projectId}/datasources", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesSavedGrafanaCredentialsOnlyForTheSameAddress() throws Exception {
        Long projectId = createProject();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/search", request -> {
            String authorization = request.getRequestHeaders().getFirst("Authorization");
            respond(request, "Bearer example-token".equals(authorization) ? 200 : 401, "[]");
        });
        server.createContext("/replacement/api/search", request -> {
            String authorization = request.getRequestHeaders().getFirst("Authorization");
            respond(request, authorization == null ? 401 : 200, "[]");
        });
        server.createContext("/redirect/api/search", request -> {
            request.getResponseHeaders().set("Location", "/api/search");
            respond(request, 302, "[]");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            saveGrafana(projectId, Map.of("name", "grafana", "baseUrl", baseUrl, "tokenEncrypted", "example-token"));
            saveGrafana(projectId, Map.of("name", "grafana renamed", "baseUrl", baseUrl));
            testDatasource(projectId, "grafana", Map.of("baseUrl", baseUrl))
                    .andExpect(jsonPath("$.data.connected").value(true));
            String replacementUrl = baseUrl + "/replacement";
            testDatasource(projectId, "grafana", Map.of("baseUrl", replacementUrl))
                    .andExpect(jsonPath("$.data.connected").value(false));
            testDatasource(projectId, "grafana", Map.of("baseUrl", baseUrl + "/redirect", "tokenEncrypted", "example-token"))
                    .andExpect(jsonPath("$.data.connected").value(false));
            testDatasource(projectId, "grafana", Map.of("baseUrl", baseUrl))
                    .andExpect(jsonPath("$.data.connected").value(true));
            saveGrafana(projectId, Map.of("name", "grafana replacement", "baseUrl", replacementUrl));
            testDatasource(projectId, "grafana", Map.of("baseUrl", replacementUrl))
                    .andExpect(jsonPath("$.data.connected").value(false));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checksJMeterSshAuthenticationWithoutSaving() throws Exception {
        Long projectId = createProject();
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("jmeter-host-key.ser")));
        server.setPasswordAuthenticator((username, password, session) ->
                "jmeter".equals(username) && "example-password".equals(password));
        server.start();
        try {
            Map<String, Object> config = new HashMap<>(Map.of(
                    "host", "127.0.0.1",
                    "sshPort", server.getPort(),
                    "sshUsername", "jmeter",
                    "sshAuthType", "password",
                    "sshPasswordEncrypted", "example-password"
            ));
            testJMeterServer(projectId, config).andExpect(jsonPath("$.data.connected").value(true));
            mockMvc.perform(get("/api/projects/{projectId}/jmeter-server", projectId))
                    .andExpect(status().isNotFound());
            config.putAll(Map.of(
                    "name", "jmeter",
                    "jmeterHome", "/opt/apache-jmeter",
                    "scriptDir", "/opt/jmeter/scripts",
                    "logDir", "/opt/jmeter/logs"
            ));
            mockMvc.perform(put("/api/projects/{projectId}/jmeter-server", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(config)))
                    .andExpect(status().isOk());
            config.put("sshPasswordEncrypted", "wrong-password");
            testJMeterServer(projectId, config).andExpect(jsonPath("$.data.connected").value(false));
            config.remove("sshPasswordEncrypted");
            testJMeterServer(projectId, config).andExpect(jsonPath("$.data.connected").value(true));
        } finally {
            server.stop();
        }
    }

    @Test
    void listsJmxFilesFromConfiguredScriptDirectory() throws Exception {
        Long projectId = createProject();
        Path scriptDir = tempDir.resolve("scripts");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("checkout.jmx"), "<jmeterTestPlan />");
        Files.writeString(scriptDir.resolve("order_query.jmx"), "<jmeterTestPlan />");
        Files.writeString(scriptDir.resolve("readme.txt"), "ignore");
        Files.createDirectories(scriptDir.resolve("nested.jmx"));
        upsertJMeterServer(projectId, scriptDir);

        mockMvc.perform(get("/api/projects/{projectId}/jmx-files", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value("checkout.jmx"))
                .andExpect(jsonPath("$.data[1]").value("order_query.jmx"));
    }

    private ResultActions testDatasource(Long projectId, String type, Map<String, String> config) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/datasources/{type}/test", projectId, type)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk());
    }

    private ResultActions testJMeterServer(Long projectId, Map<String, Object> config) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/jmeter-server/test", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk());
    }

    private void saveGrafana(Long projectId, Map<String, String> config) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/datasources/grafana", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk());
    }

    private static void respond(HttpExchange request, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(status, bytes.length);
        try (var output = request.getResponseBody()) {
            output.write(bytes);
        }
    }

    private Long createProject() throws Exception {
        String body = """
                {
                  "name": "订单系统压测",
                  "environmentName": "test"
                }
                """;
        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }

    private void upsertJMeterServer(Long projectId, Path scriptDir) throws Exception {
        String escapedScriptDir = scriptDir.toAbsolutePath().toString().replace("\\", "\\\\");
        String body = """
                {
                  "name": "jmeter-01",
                  "host": "10.0.0.10",
                  "sshPort": 22,
                  "sshUsername": "jmeter",
                  "sshAuthType": "password",
                  "sshPasswordEncrypted": "encrypted-password",
                  "jmeterHome": "/opt/apache-jmeter",
                  "scriptDir": "%s",
                  "resultDir": "/opt/jmeter/results",
                  "logDir": "/opt/jmeter/logs"
                }
                """.formatted(escapedScriptDir);
        mockMvc.perform(put("/api/projects/{projectId}/jmeter-server", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
