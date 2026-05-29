package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cleanup_plan_sqls")
public class CleanupPlanSql {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cleanupPlanId;
    private Integer stepOrder;
    private String sqlText;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
