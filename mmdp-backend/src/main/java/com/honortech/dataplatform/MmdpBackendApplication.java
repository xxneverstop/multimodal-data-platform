package com.honortech.dataplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.honortech.dataplatform.task.mapper",
        "com.honortech.dataplatform.file.mapper",
        "com.honortech.dataplatform.qc.mapper",
        "com.honortech.dataplatform.asset.mapper",
        "com.honortech.dataplatform.processing.mapper",
        "com.honortech.dataplatform.session.mapper",
        "com.honortech.dataplatform.sessionimport.mapper",
        "com.honortech.dataplatform.subject.mapper",
        "com.honortech.dataplatform.collector.mapper",
        "com.honortech.dataplatform.profile.mapper",
        "com.honortech.dataplatform.user.mapper"
})
public class MmdpBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MmdpBackendApplication.class, args);
    }
}
