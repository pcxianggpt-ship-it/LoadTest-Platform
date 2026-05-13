package com.loadtest.platform.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResultRequest {

    @NotBlank
    @Size(max = 120)
    private String name;
}
