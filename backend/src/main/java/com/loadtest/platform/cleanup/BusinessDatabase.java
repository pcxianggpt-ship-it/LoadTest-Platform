package com.loadtest.platform.cleanup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("business_databases")
public class BusinessDatabase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String databaseType;
    private String jdbcUrl;
    private String username;
    private String passwordEncrypted;
    private String status;
    private String createdAt;
    private String updatedAt;
}
