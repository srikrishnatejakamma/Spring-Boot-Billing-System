package com.acme.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.acme.platform.PlatformSecurityConfiguration;
import com.acme.platform.PlatformObservabilityConfiguration;

@SpringBootApplication
@EnableScheduling
@Import({PlatformSecurityConfiguration.class, PlatformObservabilityConfiguration.class})
public class BillingApplication {
    public static void main(String[] args) { SpringApplication.run(BillingApplication.class, args); }
}
