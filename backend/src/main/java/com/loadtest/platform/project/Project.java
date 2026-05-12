package com.loadtest.platform.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("projects")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String environmentName;
    private String status;
    private String createdAt;
    private String updatedAt;
}
