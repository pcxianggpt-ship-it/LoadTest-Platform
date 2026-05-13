package com.loadtest.platform;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.loadtest.platform", annotationClass = Mapper.class)
@SpringBootApplication
public class LoadTestPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadTestPlatformApplication.class, args);
    }
}
