package com.loadtest.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.loadtest.platform")
@SpringBootApplication
public class LoadTestPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadTestPlatformApplication.class, args);
    }
}
