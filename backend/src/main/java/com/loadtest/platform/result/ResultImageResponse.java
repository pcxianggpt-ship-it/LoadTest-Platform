package com.loadtest.platform.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultImageResponse {

    private Long id;
    private Long resultId;
    private Long projectId;
    private String imageType;
    private String title;
    private String dashboardUid;
    private String dashboardSlug;
    private Integer panelId;
    private String grafanaUrl;
    private String downloadUrl;
    private String contentType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String createdAt;

    public static ResultImageResponse from(TestResultImage image) {
        return ResultImageResponse.builder()
                .id(image.getId())
                .resultId(image.getResultId())
                .projectId(image.getProjectId())
                .imageType(image.getImageType())
                .title(image.getTitle())
                .dashboardUid(image.getDashboardUid())
                .dashboardSlug(image.getDashboardSlug())
                .panelId(image.getPanelId())
                .grafanaUrl(image.getGrafanaUrl())
                .downloadUrl("/api/result-images/" + image.getId() + "/content")
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .width(image.getWidth())
                .height(image.getHeight())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
