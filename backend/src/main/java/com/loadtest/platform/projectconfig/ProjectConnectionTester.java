package com.loadtest.platform.projectconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.loadtest.platform.ssh.SshCommandRunner;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ProjectConnectionTester {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(6);
    private final WebClient client = WebClient.create();
    private final SshCommandRunner sshCommandRunner;

    public Result testJMeterServer(JMeterServerRequest request, int port, String password) {
        String host = request.getHost();
        if (!StringUtils.hasText(host) || host.chars().anyMatch(Character::isWhitespace)
                || host.contains("/") || host.contains("@") || port < 1 || port > 65535
                || !StringUtils.hasText(request.getSshUsername())) {
            return new Result(false, "请填写有效的主机、SSH 端口和用户名。");
        }
        if (!"password".equals(request.getSshAuthType())) {
            return new Result(false, "当前压测执行仅支持 SSH 密码认证。");
        }
        if (!StringUtils.hasText(password)) {
            return new Result(false, "请输入 SSH 密码。");
        }
        try {
            sshCommandRunner.verifyPasswordConnection(host, port, request.getSshUsername(), password);
            return new Result(true, "SSH 连接和密码认证成功。");
        } catch (IOException | RuntimeException exception) {
            return new Result(false, "SSH 连接或认证失败，请检查主机、端口和凭据。");
        }
    }

    public Result testDatasource(String type, ProjectDatasource datasource) {
        URI baseUri;
        try {
            String baseUrl = datasource.getBaseUrl();
            baseUri = URI.create(baseUrl == null ? "" : baseUrl.replaceAll("/+$", ""));
            if (!("http".equals(baseUri.getScheme()) || "https".equals(baseUri.getScheme()))
                    || baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                    || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                    || baseUri.getPort() == 0 || baseUri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            return new Result(false, "请输入有效的 HTTP(S) 服务地址，地址中不要包含凭据、查询参数或片段。");
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(baseUri);
        return switch (type) {
            case "influxdb" -> testInfluxDb(uri, datasource.getDatabaseName());
            case "prometheus" -> checkJson(
                    uri.path("/api/v1/query").queryParam("query", "vector(1)").build(true).toUri(),
                    HttpHeaders.EMPTY,
                    body -> "success".equals(body.path("status").asText()) && body.path("data").path("result").isArray(),
                    "Prometheus 查询接口可用。",
                    "未收到有效的 Prometheus 查询结果，请检查服务地址。"
            );
            case "grafana" -> testGrafana(uri, datasource);
            default -> throw new IllegalArgumentException("unsupported datasource type");
        };
    }

    private Result testInfluxDb(UriComponentsBuilder uri, String databaseName) {
        if (!StringUtils.hasText(databaseName)) {
            return new Result(false, "请填写 InfluxDB 数据库名称。");
        }
        URI query = uri.path("/query")
                .queryParam("db", UriUtils.encode(databaseName, StandardCharsets.UTF_8))
                .queryParam("q", UriUtils.encode("SHOW RETENTION POLICIES", StandardCharsets.UTF_8))
                .build(true).toUri();
        return checkJson(
                query,
                HttpHeaders.EMPTY,
                body -> {
                    JsonNode results = body.path("results");
                    JsonNode firstResult = results.path(0);
                    return !body.hasNonNull("error") && results.size() == 1
                            && firstResult.isObject() && !firstResult.hasNonNull("error");
                },
                "InfluxDB 数据库可查询。",
                "InfluxDB 查询失败，请检查库名和读取权限。"
        );
    }

    private Result testGrafana(UriComponentsBuilder uri, ProjectDatasource datasource) {
        HttpHeaders credentials = new HttpHeaders();
        if (StringUtils.hasText(datasource.getTokenEncrypted())) {
            credentials.setBearerAuth(datasource.getTokenEncrypted());
        } else if (StringUtils.hasText(datasource.getPasswordEncrypted())) {
            if (!StringUtils.hasText(datasource.getUsername()) || datasource.getUsername().contains(":")) {
                return new Result(false, "请填写有效的 Grafana 用户名，或改用 Token。");
            }
            credentials.setBasicAuth(datasource.getUsername(), datasource.getPasswordEncrypted(), StandardCharsets.UTF_8);
        }
        return checkJson(
                uri.path("/api/search").queryParam("type", "dash-db").queryParam("limit", 1).build(true).toUri(),
                credentials,
                JsonNode::isArray,
                "Grafana 仪表板查询接口可用。",
                "未收到有效的 Grafana 响应，请检查服务地址和凭据。"
        );
    }

    private Result checkJson(
            URI uri, HttpHeaders headers, Predicate<JsonNode> valid,
            String successMessage, String invalidMessage
    ) {
        Result invalid = new Result(false, invalidMessage);
        return client.get().uri(uri).accept(MediaType.APPLICATION_JSON).headers(outgoing -> outgoing.putAll(headers))
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (status == 401 || status == 403) {
                        return Mono.just(new Result(false, "认证或授权失败（HTTP " + status + "），请检查凭据和权限。"));
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.just(new Result(false, "服务返回 HTTP " + status + "，请检查服务地址和运行状态。"));
                    }
                    return response.bodyToMono(JsonNode.class)
                            .map(body -> valid.test(body) ? new Result(true, successMessage) : invalid)
                            .defaultIfEmpty(invalid)
                            .onErrorReturn(invalid);
                })
                .timeout(HTTP_TIMEOUT, Mono.just(new Result(false, "连接超时，请检查服务地址和网络。")))
                .onErrorReturn(WebClientRequestException.class, new Result(false, "无法连接服务，请检查服务地址和网络。"))
                .block();
    }

    public record Result(boolean connected, String message) {
    }
}
