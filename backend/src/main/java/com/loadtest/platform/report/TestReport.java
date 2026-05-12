package com.loadtest.platform.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_reports")
public class TestReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String title;
    private String reportType;
    private String status;
    private String contentMarkdown;
    private String contentHtml;
    private String resultIdsJson;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
