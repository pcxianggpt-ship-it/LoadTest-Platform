package com.loadtest.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendWebConfigTest {

    private static final String INDEX_HTML = "<!doctype html><html><body>LoadTest frontend</body></html>";

    @DynamicPropertySource
    static void frontendProperties(DynamicPropertyRegistry registry) throws Exception {
        Path testDirectory = Files.createTempDirectory("loadtest-frontend-test-");
        Path frontendDirectory = Files.createDirectories(testDirectory.resolve("frontend"));
        Path assetsDirectory = Files.createDirectories(frontendDirectory.resolve("assets"));
        Files.writeString(frontendDirectory.resolve("index.html"), INDEX_HTML);
        Files.writeString(assetsDirectory.resolve("app.js"), "console.log('LoadTest');");
        Files.writeString(assetsDirectory.resolve("app.css"), "body { color: black; }");
        registry.add("LOADTEST_FRONTEND_DIR", () -> frontendDirectory.toString().replace('\\', '/'));
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + testDirectory.resolve("test.db"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @ParameterizedTest
    @ValueSource(strings = {
            "/", "/index.html", "/projects/1", "/projects/1/tasks/new", "/projects/1/executions",
            "/tasks", "/tasks/new", "/tasks/1/edit", "/cleanup", "/executions",
            "/results", "/results/1", "/reports", "/reports/1?refresh=true"
    })
    void servesFrontendPages(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.TEXT_HTML));
        assertThat(response.getBody()).isEqualTo(INDEX_HTML);
    }

    @Test
    void servesFrontendAssets() {
        ResponseEntity<String> script = restTemplate.getForEntity("/assets/app.js", String.class);
        assertThat(script.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(script.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.getSubtype().contains("javascript"));
        assertThat(script.getBody()).isEqualTo("console.log('LoadTest');");

        ResponseEntity<String> stylesheet = restTemplate.getForEntity("/assets/app.css", String.class);
        assertThat(stylesheet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stylesheet.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("text/css"));
        assertThat(stylesheet.getBody()).isEqualTo("body { color: black; }");
    }

    @Test
    void supportsHeadRequestsForAssets() {
        ResponseEntity<String> response = restTemplate.exchange("/assets/app.js", HttpMethod.HEAD, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentLength()).isGreaterThan(0);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void keepsApiAndHealthResponses() {
        ResponseEntity<String> projects = restTemplate.getForEntity("/api/projects", String.class);
        assertThat(projects.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(projects.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(projects.getBody()).contains("\"success\":true", "\"data\":[]");

        ResponseEntity<String> health = restTemplate.getForEntity("/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isEqualTo("{\"status\":\"ok\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/missing.js", "/assets/missing.css", "/api/missing", "/api", "/unknown-page"})
    void returnsNotFoundWithoutFrontendFallback(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).doesNotContain("LoadTest frontend");
    }
}
