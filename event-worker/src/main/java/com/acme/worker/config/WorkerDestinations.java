package com.acme.worker.config;

import com.acme.billing.integrations.*;
import com.acme.worker.messaging.ReportTransport;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.ratelimiter.*;
import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WorkerDestinations {
    @Bean @Profile("!demo") public AccountDestination externalDestination(MongoTemplate mongo, RestClient.Builder builder,
            MeterRegistry metrics, @Value("${worker.platform-url}") String url,
            @Value("${worker.rate-limit-per-second:20}") int rate,
            @Value("${worker.circuit-failure-threshold:50}") float threshold) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(2000); factory.setReadTimeout(3000);
        var raw = new HttpMongoDestination(builder.baseUrl(url).requestFactory(factory).build(), mongo);
        var circuit = CircuitBreaker.of("billing-platform", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(4).failureRateThreshold(threshold)
                .waitDurationInOpenState(Duration.ofSeconds(10)).permittedNumberOfCallsInHalfOpenState(1).build());
        var limiter = RateLimiter.of("billing-platform", RateLimiterConfig.custom().limitForPeriod(rate)
                .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ZERO).build());
        Gauge.builder("billing.platform.circuit.open", circuit, c -> c.getState() == CircuitBreaker.State.OPEN ? 1 : 0).register(metrics);
        return new ResilientDestination(raw, circuit, limiter);
    }
    @Bean @Profile("!demo") public MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return settings -> settings.applyToClusterSettings(c -> c.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s.connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS));
    }
    @Bean @Profile("demo") public DemoDestination demoDestination() { return new DemoDestination(); }
    @Bean @Profile("demo") public ReportTransport localReportTransport() { return report -> {}; }
}
