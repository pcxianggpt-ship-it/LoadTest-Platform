package com.loadtest.platform.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshCommandRunnerTest {

    @TempDir
    private Path tempDir;

    @Test
    void createsClientThatTrustsConfiguredJMeterServerHostKey() throws Exception {
        try (var client = new SshCommandRunner().createClient()) {
            var publicKey = KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic();

            boolean accepted = client.getServerKeyVerifier().verifyServerKey(
                    null,
                    new InetSocketAddress("192.168.65.139", 22),
                    publicKey
            );

            assertThat(accepted).isTrue();
        }
    }

    @Test
    void acceptsServerHostKeyWhenRunningPasswordCommand() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("host-key.ser")));
        server.setPasswordAuthenticator((username, password, session) ->
                "root".equals(username) && "secret".equals(password));
        server.setCommandFactory((channel, command) -> new FixedOutputCommand(command));
        server.start();
        try {
            SshCommandResult result = new SshCommandRunner().runWithPassword(
                    "127.0.0.1",
                    server.getPort(),
                    "root",
                    "secret",
                    "jmeter -n",
                    Duration.ofSeconds(5)
            );

            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("ran: bash -lc 'jmeter -n'");
            assertThat(result.getStderr()).isEmpty();
        } finally {
            server.stop();
        }
    }

    @Test
    void wrapsCommandInLoginShellAndEscapesSingleQuotes() {
        String command = new SshCommandRunner().toLoginShellCommand(
                "/opt/apache-jmeter/bin/jmeter -n -t /opt/scripts/order's.jmx"
        );

        assertThat(command).isEqualTo(
                "bash -lc '/opt/apache-jmeter/bin/jmeter -n -t /opt/scripts/order'\"'\"'s.jmx'"
        );
    }

    private static class FixedOutputCommand implements Command {

        private final String command;
        private OutputStream stdout;
        private OutputStream stderr;
        private ExitCallback exitCallback;

        private FixedOutputCommand(String command) {
            this.command = command;
        }

        @Override
        public void setInputStream(InputStream inputStream) {
        }

        @Override
        public void setOutputStream(OutputStream outputStream) {
            this.stdout = outputStream;
        }

        @Override
        public void setErrorStream(OutputStream outputStream) {
            this.stderr = outputStream;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exitCallback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) throws IOException {
            stdout.write(("ran: " + command).getBytes(StandardCharsets.UTF_8));
            stderr.flush();
            stdout.flush();
            exitCallback.onExit(0);
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }
}
