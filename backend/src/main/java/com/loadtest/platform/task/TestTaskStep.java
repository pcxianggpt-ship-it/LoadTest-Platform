package com.loadtest.platform.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("test_task_steps")
public class TestTaskStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer stepOrder;
    private String stepName;
    private String jmxFile;
    private Integer threads;
    private Integer durationSeconds;
    private Integer rampUpSeconds;
    private Boolean saveJtl;
    private String jmeterArgsJson;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
