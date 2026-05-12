package com.loadtest.platform.jmeter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JMeterCommand {

    private String command;
    private String logPath;
    private String jtlPath;
}
