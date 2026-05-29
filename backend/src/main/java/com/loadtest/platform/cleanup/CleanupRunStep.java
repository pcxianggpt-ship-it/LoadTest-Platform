package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cleanup_run_steps")
public class CleanupRunStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cleanupRunId;
    private Integer stepOrder;
    private String sqlText;
    private String status;
    private Integer affectedRows;
    private String errorMessage;
    private String startedAt;
    private String endedAt;
    private String createdAt;
    private String updatedAt;
}
