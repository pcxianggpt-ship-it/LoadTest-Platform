package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cleanup_plans")
public class CleanupPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long businessDatabaseId;
    private String name;
    private String description;
    private Boolean enabled;
    private String status;
    private String createdAt;
    private String updatedAt;
}
