package com.loadtest.platform.grafana;

public record GrafanaRenderRequest(
        String dashboardUid,
        String dashboardSlug,
        Integer panelId,
        String from,
        String to,
        Integer orgId,
        Integer width,
        Integer height,
        String theme
) {
}
