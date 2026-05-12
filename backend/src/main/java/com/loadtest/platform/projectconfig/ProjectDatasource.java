package com.loadtest.platform.projectconfig;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("project_datasources")
public class ProjectDatasource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String type;
    private String name;
    private String baseUrl;
    private String databaseName;
    private String username;
    private String passwordEncrypted;
    private String tokenEncrypted;
    private String extraConfigJson;
    private String status;
    private String createdAt;
    private String updatedAt;
}
