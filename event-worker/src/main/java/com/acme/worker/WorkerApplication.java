package com.acme.worker;

import com.acme.platform.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableScheduling
@Import({PlatformSecurityConfiguration.class, PlatformObservabilityConfiguration.class})
public class WorkerApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(WorkerApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "worker"));
        app.run(args);
    }
}
