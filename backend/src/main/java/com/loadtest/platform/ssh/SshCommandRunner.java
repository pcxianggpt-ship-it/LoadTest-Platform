package com.loadtest.platform.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Component;

@Component
public class SshCommandRunner {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    public void verifyPasswordConnection(String host, int port, String username, String password) throws IOException {
        try (SshClient client = createClient()) {
            client.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE));
            client.start();
            try (ClientSession session = client.connect(username, host, port).verify(TEST_TIMEOUT).getSession()) {
                session.addPasswordIdentity(password);
                session.auth().verify(TEST_TIMEOUT);
            }
        }
    }

    public SshCommandResult runWithPassword(
            String host,
            int port,
            String username,
            String password,
            String command,
            Duration commandTimeout
    ) throws Exception {
        SshClient client = createClient();
        client.start();
        try {
            try (ClientSession session = client.connect(username, host, port)
                    .verify(CONNECT_TIMEOUT)
                    .getSession()) {
                session.addPasswordIdentity(password);
                session.auth().verify(CONNECT_TIMEOUT);
                return runCommand(session, command, commandTimeout);
            }
        } finally {
            client.stop();
        }
    }

    SshClient createClient() {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((session, remoteAddress, serverKey) -> true);
        return client;
    }

    private SshCommandResult runCommand(
            ClientSession session,
            String command,
            Duration commandTimeout
    ) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (ClientChannel channel = session.createExecChannel(toLoginShellCommand(command))) {
            channel.setOut(stdout);
            channel.setErr(stderr);
            channel.open().verify(CONNECT_TIMEOUT);
            channel.waitFor(
                    java.util.Set.of(ClientChannelEvent.CLOSED),
                    commandTimeout.toMillis()
            );
            Integer exitStatus = channel.getExitStatus();
            return SshCommandResult.builder()
                    .exitCode(exitStatus == null ? -1 : exitStatus)
                    .stdout(stdout.toString(StandardCharsets.UTF_8))
                    .stderr(stderr.toString(StandardCharsets.UTF_8))
                    .build();
        }
    }

    String toLoginShellCommand(String command) {
        return "bash -lc '" + command.replace("'", "'\"'\"'") + "'";
    }
}
