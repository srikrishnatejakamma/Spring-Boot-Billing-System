package com.acme.worker.messaging;

import com.acme.billing.dto.EventEnvelope;
import com.acme.worker.service.InboxService;
import com.acme.worker.persistence.InboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.concurrent.TimeUnit;

@Configuration @Profile("!demo")
public class WorkerBroker {
    @Bean public Declarables workerTopology(@Value("${worker.queue:billing.account-sync}") String name,
                                            @Value("${worker.routing-key:account.changed}") String route) {
        var exchange = new DirectExchange("billing.events", true, false);
        var queue = QueueBuilder.durable(name).deadLetterExchange("billing.dead").build();
        var poison = QueueBuilder.durable(name + ".poison").build();
        var dead = new DirectExchange("billing.dead", true, false);
        var businessDead = QueueBuilder.durable("billing.delivery-dead").build();
        return new Declarables(exchange, queue, dead, poison, businessDead,
                BindingBuilder.bind(queue).to(exchange).with(route), BindingBuilder.bind(poison).to(dead).with(route),
                BindingBuilder.bind(businessDead).to(exchange).with("account.delivery.dead"));
    }
    @Bean public ReportTransport resultTransport(RabbitTemplate rabbit, ObjectMapper json) {
        rabbit.setMandatory(true); rabbit.setObservationEnabled(true);
        return report -> {
            send(rabbit, json.writeValueAsString(report), "account.delivery.result", report.eventId().toString());
            if ("DEAD_LETTER".equals(report.status())) send(rabbit, json.writeValueAsString(report), "account.delivery.dead", report.eventId().toString());
        };
    }
    private void send(RabbitTemplate rabbit, String payload, String route, String id) throws Exception {
        var correlation = new CorrelationData(id);
        rabbit.convertAndSend("billing.events", route, payload, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); return message;
        }, correlation);
        if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null)
            throw new IllegalStateException("Report routing was not confirmed");
    }
    @Bean public Consumer consumer(InboxService inbox, InboxRepository receipts, ObjectMapper json,
                                    @Value("${worker.cohort:main}") String cohort) { return new Consumer(inbox, receipts, json, cohort); }
    public static class Consumer {
        private static final Logger log = LoggerFactory.getLogger(Consumer.class);
        private final InboxService inbox; private final InboxRepository receipts; private final ObjectMapper json; private final String cohort;
        public Consumer(InboxService inbox, InboxRepository receipts, ObjectMapper json, String cohort) {
            this.inbox = inbox; this.receipts = receipts; this.json = json; this.cohort = cohort;
        }
        @RabbitListener(queues = "${worker.queue:billing.account-sync}")
        public void receive(String payload) throws Exception {
            EventEnvelope event;
            try { event = json.readValue(payload, EventEnvelope.class); }
            catch (Exception exception) { log.warn("invalid_event_envelope cause={}", exception.getClass().getSimpleName()); throw new AmqpRejectAndDontRequeueException("Invalid event envelope"); }
            try { inbox.accept(event, cohort); }
            catch (DataIntegrityViolationException exception) {
                var receipt = receipts.findById(event.change().eventId().toString());
                if (receipt.isEmpty() || !receipt.get().payload.equals(json.writeValueAsString(event.change()))) throw exception;
            }
        }
    }
}
