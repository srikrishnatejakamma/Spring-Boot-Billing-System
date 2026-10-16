package com.acme.platform;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;

@Configuration
public class PlatformObservabilityConfiguration {
    @Bean public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelation() {
        var registration = new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); return registration;
    }
}
