package com.loadtest.platform.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResultImageRequest {

    @Size(max = 120)
    private String title;

    @Size(max = 120)
    private String dashboardUid;

    @Size(max = 160)
    private String dashboardSlug;

    @NotNull
    @Min(1)
    private Integer panelId;

    @Min(1)
    private Integer orgId;

    @Min(320)
    @Max(3840)
    private Integer width;

    @Min(240)
    @Max(2160)
    private Integer height;

    @Size(max = 20)
    private String theme;
}
