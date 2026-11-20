package com.jobtrack;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.jobtrack.system.mapper", "com.jobtrack.application.mapper"})
public class JobTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTrackApplication.class, args);
    }
}
