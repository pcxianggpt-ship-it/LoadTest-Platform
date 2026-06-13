package com.loadtest.platform.grafana;

import com.loadtest.platform.projectconfig.ProjectDatasource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DefaultGrafanaImageClient implements GrafanaImageClient {

    private final RestTemplate restTemplate;

    public DefaultGrafanaImageClient() {
        this(new RestTemplate());
    }

    DefaultGrafanaImageClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public GrafanaImageData renderPanel(ProjectDatasource datasource, GrafanaRenderRequest request) {
        URI uri = renderUri(datasource, request);
        HttpHeaders headers = new HttpHeaders();
        if (datasource.getTokenEncrypted() != null && !datasource.getTokenEncrypted().isBlank()) {
            headers.setBearerAuth(datasource.getTokenEncrypted());
        } else if (datasource.getUsername() != null && !datasource.getUsername().isBlank()
                && datasource.getPasswordEncrypted() != null && !datasource.getPasswordEncrypted().isBlank()) {
            String basic = datasource.getUsername() + ":" + datasource.getPasswordEncrypted();
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        }

        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("Grafana returned empty image");
        }
        String contentType = response.getHeaders().getContentType() == null
                ? "image/png"
                : response.getHeaders().getContentType().toString();
        if (!contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalStateException("Grafana returned non-image content: " + contentType);
        }
        return new GrafanaImageData(body, contentType, uri.toString());
    }

    private URI renderUri(ProjectDatasource datasource, GrafanaRenderRequest request) {
        String slug = request.dashboardSlug() == null || request.dashboardSlug().isBlank()
                ? "_"
                : request.dashboardSlug();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(datasource.getBaseUrl()))
                .pathSegment("render", "d-solo", request.dashboardUid(), slug)
                .queryParam("panelId", request.panelId())
                .queryParam("from", request.from())
                .queryParam("to", request.to())
                .queryParam("width", request.width())
                .queryParam("height", request.height())
                .queryParam("theme", request.theme());
        if (request.orgId() != null) {
            builder.queryParam("orgId", request.orgId());
        }
        return builder.build().encode().toUri();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
