package com.scaffold.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan({
        "com.scaffold.system.mapper",
        "com.scaffold.system.job.mapper",
        "com.scaffold.system.notice.mapper",
        "com.scaffold.generator.mapper",
        "com.scaffold.vuln.mapper"
})
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.scaffold")
public class ScaffoldAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScaffoldAdminApplication.class, args);
    }
}
