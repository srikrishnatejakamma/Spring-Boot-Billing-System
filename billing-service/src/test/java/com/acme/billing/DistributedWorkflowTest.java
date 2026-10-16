package com.acme.billing;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import com.acme.billing.events.*;
import com.acme.billing.web.OperationsService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("infrastructure")
@Testcontainers
@ActiveProfiles("distributed")
@SpringBootTest(properties = {"billing.scheduler.enabled=false", "billing.seed=false"})
class DistributedWorkflowTest {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    static final java.util.Set<String> received = ConcurrentHashMap.newKeySet();
    static final java.util.concurrent.atomic.AtomicInteger platformCalls = new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicBoolean platformAvailable = new java.util.concurrent.atomic.AtomicBoolean(true);
    static final HttpServer platform = platform();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("billing.platform-url", () -> "http://127.0.0.1:" + platform.getAddress().getPort());
    }
    @Autowired AccountService accounts;
    @Autowired OutboxRepository events;
    @Autowired SyncRepository syncs;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired MongoTemplate projection;
    @Autowired RabbitTemplate messages;
    @Autowired MeterRegistry metrics;
    @Autowired OperationsService operations;

    @Test void realBrokerDeliversIntoMongoAndDuplicateMessagesAreHarmless() {
        var account = accounts.create(new AccountCommands.Create("Distributed Acme", "finance@acme.example", Plan.ENTERPRISE), "infrastructure-key-01");
        var event = events.findByAccountIdOrderByCreatedAtDesc(account.id).getFirst();
        dispatcher.dispatch(event.id);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(events.findById(event.id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
            assertThat(syncs.findById(account.id).orElseThrow().syncedVersion).isZero();
        });
        messages.convertAndSend("billing.events", "account.changed", event.id);
        messages.convertAndSend("billing.events", "account.changed", event.id);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(metrics.counter("billing.delivery.duplicates").count()).isGreaterThanOrEqualTo(2);
            assertThat(platformCalls.get()).isEqualTo(1);
            assertThat(received).containsExactly(event.id);
            Document document = projection.findById(account.id, Document.class, "account_views");
            assertThat(document).isNotNull();
            assertThat(document.get("monthlyRate")).isEqualTo("499.00");
            assertThat(((Number) document.get("version")).longValue()).isZero();
        });

        platformAvailable.set(false);
        accounts.update(account.id, new AccountCommands.Update("Distributed Acme", account.billingEmail,
                Plan.GROWTH, AccountStatus.ACTIVE, 0L));
        var changed = events.findByAccountIdOrderByCreatedAtDesc(account.id).getFirst();
        dispatcher.dispatch(changed.id);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(events.findById(changed.id).orElseThrow().status).isEqualTo(EventStatus.RETRYING));
        assertThat(projection.findById(account.id, Document.class, "account_views").get("monthlyRate")).isEqualTo("499.00");
        platformAvailable.set(true);
        operations.retry(changed.id); dispatcher.dispatch(changed.id);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(events.findById(changed.id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
            assertThat(projection.findById(account.id, Document.class, "account_views").get("monthlyRate")).isEqualTo("149.00");
        });
    }
    @AfterAll static void stopPlatform() { platform.stop(0); }
    static HttpServer platform() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/accounts", exchange -> {
                exchange.getRequestBody().readAllBytes();
                if (!platformAvailable.get()) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                platformCalls.incrementAndGet();
                received.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                exchange.sendResponseHeaders(200, -1); exchange.close();
            });
            server.start(); return server;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
