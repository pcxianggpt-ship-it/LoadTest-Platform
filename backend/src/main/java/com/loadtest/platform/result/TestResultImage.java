package com.loadtest.platform.result;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_result_images")
public class TestResultImage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private Long projectId;
    private String imageType;
    private String title;
    private String dashboardUid;
    private String dashboardSlug;
    private Integer panelId;
    private String grafanaUrl;
    private String filePath;
    private String contentType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String createdAt;
}
