package com.acme.billing;

import com.acme.billing.dto.*;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import com.acme.billing.events.*;
import com.acme.billing.web.OperationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
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
@ActiveProfiles({"distributed", "security-test"})
@SpringBootTest(properties = {
        "billing.scheduler.enabled=false", "billing.seed=false", "spring.cloud.config.enabled=false",
        "BILLING_CONFIG_IMPORT=optional:classpath:platform-defaults.yml",
        "spring.flyway.baseline-on-migrate=true", "spring.flyway.baseline-version=0"})
class DistributedWorkflowTest {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    static final java.util.Set<String> received = ConcurrentHashMap.newKeySet();
    static final java.util.concurrent.atomic.AtomicInteger platformCalls = new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicBoolean platformAvailable = new java.util.concurrent.atomic.AtomicBoolean(true);
    static final HttpServer platform = platform();
    static ConfigurableApplicationContext worker;
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
    @BeforeAll static void startWorker() {
        worker = new SpringApplicationBuilder(com.acme.worker.WorkerApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("spring.config.name=worker")
                .run("--spring.profiles.active=distributed,security-test",
                        "--spring.cloud.config.enabled=false",
                        "--WORKER_CONFIG_IMPORT=optional:classpath:platform-defaults.yml",
                        "--spring.datasource.url=" + mysql.getJdbcUrl(),
                        "--spring.datasource.username=" + mysql.getUsername(),
                        "--spring.datasource.password=" + mysql.getPassword(),
                        "--spring.flyway.table=worker_flyway_schema_history",
                        "--spring.flyway.baseline-on-migrate=true",
                        "--spring.flyway.baseline-version=0",
                        "--spring.rabbitmq.host=" + rabbit.getHost(),
                        "--spring.rabbitmq.port=" + rabbit.getAmqpPort(),
                        "--spring.rabbitmq.username=" + rabbit.getAdminUsername(),
                        "--spring.rabbitmq.password=" + rabbit.getAdminPassword(),
                        "--spring.data.mongodb.uri=" + mongo.getReplicaSetUrl(),
                        "--worker.platform-url=http://127.0.0.1:" + platform.getAddress().getPort());
    }
    @Autowired AccountService accounts;
    @Autowired OutboxRepository events;
    @Autowired SyncRepository syncs;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired MongoTemplate projection;
    @Autowired RabbitTemplate messages;
    @Autowired MeterRegistry metrics;
    @Autowired OperationsService operations;
    @Autowired ObjectMapper json;

    @Test void realBrokerDeliversIntoMongoAndDuplicateMessagesAreHarmless() throws Exception {
        var account = accounts.create(new AccountCommands.Create("Distributed Acme", "finance@acme.example", Plan.ENTERPRISE), "infrastructure-key-01");
        var event = events.findByAccountIdOrderByCreatedAtDesc(account.id).getFirst();
        dispatcher.dispatch(event.id);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(events.findById(event.id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
            assertThat(syncs.findById(account.id).orElseThrow().syncedVersion).isZero();
        });
        String envelope = json.writeValueAsString(new EventEnvelope(1, json.readValue(event.payload, AccountChanged.class), event.generation));
        messages.convertAndSend("billing.events", "account.changed", envelope);
        messages.convertAndSend("billing.events", "account.changed", envelope);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
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
    @AfterAll static void stopPlatform() { if (worker != null) worker.close(); platform.stop(0); }
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
