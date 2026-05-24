package com.loadtest.platform.projectconfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
