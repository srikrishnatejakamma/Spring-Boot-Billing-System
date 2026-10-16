package com.acme.billing.config;

import com.acme.billing.events.*;
import com.acme.billing.integrations.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.concurrent.TimeUnit;

@Configuration @Profile("distributed")
public class DistributedConfiguration {
    public static final String EXCHANGE = "billing.events";
    public static final String QUEUE = "billing.account-sync";
    @Bean public Declarables topology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange("billing.dead").build();
        var deadExchange = new DirectExchange("billing.dead", true, false);
        var deadQueue = QueueBuilder.durable("billing.poison").build();
        return new Declarables(exchange, queue, deadExchange, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with("account.changed"),
                BindingBuilder.bind(deadQueue).to(deadExchange).with("account.changed"));
    }
    @Bean public EventTransport rabbitTransport(RabbitTemplate rabbit) {
        rabbit.setMandatory(true);
        return eventId -> {
            var correlation = new CorrelationData(eventId);
            rabbit.convertAndSend(EXCHANGE, "account.changed", eventId, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null)
                throw new IllegalStateException("Broker did not confirm a routed message");
        };
    }
    @Bean public AccountDestination httpMongoDestination(MongoTemplate mongo,
            @Value("${billing.platform-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(3000);
        return new HttpMongoDestination(RestClient.builder().baseUrl(url).requestFactory(factory).build(), mongo);
    }
    @Bean public org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return settings -> settings
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS));
    }
    @Bean public RabbitConsumer rabbitConsumer(DeliveryProcessor processor) { return new RabbitConsumer(processor); }
    public static class RabbitConsumer {
        private final DeliveryProcessor processor;
        public RabbitConsumer(DeliveryProcessor processor) { this.processor = processor; }
        @RabbitListener(queues = QUEUE)
        public void receive(String eventId) { processor.receive(eventId); }
    }
}
