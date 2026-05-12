package com.loadtest.platform.ssh;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SshCommandResult {

    private int exitCode;
    private String stdout;
    private String stderr;
}
