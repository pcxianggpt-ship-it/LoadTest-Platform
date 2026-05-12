package com.loadtest.platform.projectconfig;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("jmeter_servers")
public class JMeterServer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUsername;
    private String sshAuthType;
    private String sshPasswordEncrypted;
    private String sshPrivateKeyEncrypted;
    private String jmeterHome;
    private String scriptDir;
    private String resultDir;
    private String logDir;
    private String status;
    private String createdAt;
    private String updatedAt;
}
