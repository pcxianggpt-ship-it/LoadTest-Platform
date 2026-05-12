package com.loadtest.platform.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestTaskRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String description;
    private Boolean defaultSaveJtl = false;

    @Valid
    @NotNull
    private StepRequest step;

    @Data
    public static class StepRequest {

        @NotBlank
        @Size(max = 120)
        private String stepName;

        @JmxFile
        private String jmxFile;

        @NotNull
        @Min(1)
        private Integer threads;

        @NotNull
        @Min(1)
        private Integer durationSeconds;

        @NotNull
        @Min(0)
        private Integer rampUpSeconds;

        private Boolean saveJtl;
        private String jmeterArgsJson;
    }
}
