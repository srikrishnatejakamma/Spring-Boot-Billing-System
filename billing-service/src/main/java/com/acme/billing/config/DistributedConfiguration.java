package com.acme.billing.config;

import com.acme.billing.events.*;
import com.acme.billing.dto.DeliveryReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import java.util.concurrent.TimeUnit;

@Configuration @Profile("distributed")
public class DistributedConfiguration {
    public static final String EXCHANGE = "billing.events";
    @Bean public Declarables topology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var results = QueueBuilder.durable("billing.delivery-results").deadLetterExchange("billing.dead").build();
        var dead = new DirectExchange("billing.dead", true, false);
        var poison = QueueBuilder.durable("billing.result-poison").build();
        return new Declarables(exchange, results, dead, poison,
                BindingBuilder.bind(results).to(exchange).with("account.delivery.result"),
                BindingBuilder.bind(poison).to(dead).with("account.delivery.result"));
    }
    @Bean public EventTransport rabbitTransport(RabbitTemplate rabbit, ObjectMapper json) {
        rabbit.setMandatory(true); rabbit.setObservationEnabled(true);
        return (event, routingKey) -> {
            var correlation = new CorrelationData(event.change().eventId().toString());
            rabbit.convertAndSend(EXCHANGE, routingKey, json.writeValueAsString(event), message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); return message;
            }, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Broker did not confirm routing");
        };
    }
    @Bean public ResultConsumer resultConsumer(DeliveryReports reports, ObjectMapper json) { return new ResultConsumer(reports, json); }
    public static class ResultConsumer {
        private final DeliveryReports reports; private final ObjectMapper json;
        public ResultConsumer(DeliveryReports reports, ObjectMapper json) { this.reports = reports; this.json = json; }
        @RabbitListener(queues = "billing.delivery-results")
        public void receive(String payload) throws Exception { reports.accept(json.readValue(payload, DeliveryReport.class)); }
    }
}
