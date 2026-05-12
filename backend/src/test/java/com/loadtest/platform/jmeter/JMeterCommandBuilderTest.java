package com.loadtest.platform.jmeter;

import static org.assertj.core.api.Assertions.assertThat;

import com.loadtest.platform.projectconfig.JMeterServer;
import com.loadtest.platform.task.TestTaskStep;
import org.junit.jupiter.api.Test;

class JMeterCommandBuilderTest {

    @Test
    void buildsCommandWithoutJtlWhenSaveJtlIsFalse() {
        JMeterServer server = server();
        TestTaskStep step = step(false);

        JMeterCommand command = new JMeterCommandBuilder().build(server, step, 123L);

        assertThat(command.getCommand()).contains("/opt/apache-jmeter/bin/jmeter -n");
        assertThat(command.getCommand()).contains("-t /opt/jmeter/scripts/order_query.jmx");
        assertThat(command.getCommand()).contains("-Jthreads=100");
        assertThat(command.getCommand()).contains("-Jduration=600");
        assertThat(command.getCommand()).contains("-Jramp_up=60");
        assertThat(command.getCommand()).contains("-j /opt/jmeter/logs/execution_123_step_1.log");
        assertThat(command.getCommand()).doesNotContain(" -l ");
        assertThat(command.getJtlPath()).isNull();
        assertThat(command.getLogPath()).isEqualTo("/opt/jmeter/logs/execution_123_step_1.log");
    }

    @Test
    void appendsJtlPathWhenSaveJtlIsTrue() {
        JMeterServer server = server();
        TestTaskStep step = step(true);

        JMeterCommand command = new JMeterCommandBuilder().build(server, step, 456L);

        assertThat(command.getCommand()).contains(
                "-l /opt/jmeter/results/execution_456_step_1.jtl"
        );
        assertThat(command.getJtlPath()).isEqualTo("/opt/jmeter/results/execution_456_step_1.jtl");
    }

    @Test
    void trimsDuplicateSlashesWhenJoiningPaths() {
        JMeterServer server = server();
        server.setJmeterHome("/opt/apache-jmeter/");
        server.setScriptDir("/opt/jmeter/scripts/");
        server.setLogDir("/opt/jmeter/logs/");
        server.setResultDir("/opt/jmeter/results/");

        JMeterCommand command = new JMeterCommandBuilder().build(server, step(true), 789L);

        assertThat(command.getCommand()).contains("/opt/apache-jmeter/bin/jmeter");
        assertThat(command.getCommand()).contains("-t /opt/jmeter/scripts/order_query.jmx");
        assertThat(command.getCommand()).contains("-j /opt/jmeter/logs/execution_789_step_1.log");
        assertThat(command.getCommand()).contains("-l /opt/jmeter/results/execution_789_step_1.jtl");
    }

    private JMeterServer server() {
        JMeterServer server = new JMeterServer();
        server.setJmeterHome("/opt/apache-jmeter");
        server.setScriptDir("/opt/jmeter/scripts");
        server.setLogDir("/opt/jmeter/logs");
        server.setResultDir("/opt/jmeter/results");
        return server;
    }

    private TestTaskStep step(boolean saveJtl) {
        TestTaskStep step = new TestTaskStep();
        step.setStepOrder(1);
        step.setJmxFile("order_query.jmx");
        step.setThreads(100);
        step.setDurationSeconds(600);
        step.setRampUpSeconds(60);
        step.setSaveJtl(saveJtl);
        return step;
    }
}
