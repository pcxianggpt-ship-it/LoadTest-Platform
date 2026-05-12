package com.loadtest.platform.ssh;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Component;

@Component
public class SshCommandRunner {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    public SshCommandResult runWithPassword(
            String host,
            int port,
            String username,
            String password,
            String command,
            Duration commandTimeout
    ) throws Exception {
        SshClient client = SshClient.setUpDefaultClient();
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

    private SshCommandResult runCommand(
            ClientSession session,
            String command,
            Duration commandTimeout
    ) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (ClientChannel channel = session.createExecChannel(command)) {
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
}
