package com.loadtest.platform.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_tasks")
public class TestTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private Boolean defaultSaveJtl;
    private String status;
    private String createdAt;
    private String updatedAt;
}
