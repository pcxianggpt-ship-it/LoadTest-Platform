package com.loadtest.platform.metrics;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

final class TestHttp {

    private TestHttp() {
    }

    static Map<String, String> queryParams(String query) {
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts.length > 1 ? parts[1] : ""));
    }
}
