package com.loadtest.platform.metrics;

public record K8sPodSelector(String namespace, String podRegex) {
    String prometheusPodRegex() {
        return "*".equals(podRegex) ? ".*" : podRegex;
    }
}
