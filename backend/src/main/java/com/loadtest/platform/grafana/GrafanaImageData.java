package com.loadtest.platform.grafana;

public record GrafanaImageData(byte[] content, String contentType, String renderUrl) {
}
