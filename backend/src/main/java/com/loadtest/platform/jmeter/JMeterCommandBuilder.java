package com.loadtest.platform.jmeter;

import com.loadtest.platform.projectconfig.JMeterServer;
import com.loadtest.platform.task.TestTaskStep;
import org.springframework.stereotype.Component;

@Component
public class JMeterCommandBuilder {

    public JMeterCommand build(JMeterServer server, TestTaskStep step, Long executionId) {
        String stepSuffix = "execution_" + executionId + "_step_" + step.getStepOrder();
        String jmeterBinary = joinPath(server.getJmeterHome(), "bin/jmeter");
        String jmxPath = joinPath(server.getScriptDir(), step.getJmxFile());
        String logPath = joinPath(server.getLogDir(), stepSuffix + ".log");

        StringBuilder command = new StringBuilder()
                .append(jmeterBinary)
                .append(" -n")
                .append(" -t ").append(jmxPath)
                .append(" -Jthreads=").append(step.getThreads())
                .append(" -Jduration=").append(step.getDurationSeconds())
                .append(" -Jramp_up=").append(step.getRampUpSeconds())
                .append(" -j ").append(logPath);

        String jtlPath = null;
        if (Boolean.TRUE.equals(step.getSaveJtl())) {
            jtlPath = joinPath(server.getResultDir(), stepSuffix + ".jtl");
            command.append(" -l ").append(jtlPath);
        }

        return JMeterCommand.builder()
                .command(command.toString())
                .logPath(logPath)
                .jtlPath(jtlPath)
                .build();
    }

    private String joinPath(String base, String child) {
        String normalizedBase = trimTrailingSlash(base);
        String normalizedChild = trimLeadingSlash(child);
        return normalizedBase + "/" + normalizedChild;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String trimLeadingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("^/+", "");
    }
}
