package com.loadtest.platform.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "loadtest.metrics.influx.jmeter")
public class JMeterInfluxProperties {

    private String measurement = "jmeter";
    private int sendIntervalSeconds = 5;
}
