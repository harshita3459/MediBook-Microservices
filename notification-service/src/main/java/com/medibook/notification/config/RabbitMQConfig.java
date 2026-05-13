package com.medibook.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQConfig — declares every queue, exchange, and binding for MediBook.
 *
 * ARCHITECTURE OVERVIEW:
 * ──────────────────────
 * Producer (other services) → Exchange → Binding → Queue → Consumer (this service)
 *
 * We use a TOPIC exchange so routing keys can be pattern-matched:
 *   "appointment.booking.confirmed"  → booking.queue
 *   "appointment.cancelled"          → cancellation.queue
 *   "appointment.reminder.*"         → reminder.queue
 *
 * DEAD LETTER QUEUES (DLQ):
 * ──────────────────────────
 * Every queue has a paired DLQ. If message processing fails 3 times (see retry
 * config in application.yml), RabbitMQ routes the message to the DLQ instead
 * of dropping it. You can inspect and reprocess DLQ messages from the
 * RabbitMQ Management UI at http://localhost:15672.
 *
 * QUEUE NAMING CONVENTION:
 *   medibook.{domain}.queue      → main processing queue
 *   medibook.{domain}.dlq        → dead letter queue (failed messages)
 *   medibook.{domain}.exchange   → topic exchange for routing
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange names ─────────────────────────────────────────────────────────
    public static final String NOTIFICATION_EXCHANGE = "medibook.notification.exchange";
    public static final String DEAD_LETTER_EXCHANGE  = "medibook.dlx";

    // ── Queue names ────────────────────────────────────────────────────────────
    public static final String BOOKING_QUEUE       = "medibook.booking.queue";
    public static final String CANCELLATION_QUEUE  = "medibook.cancellation.queue";
    public static final String REMINDER_QUEUE      = "medibook.reminder.queue";
    public static final String PAYMENT_QUEUE       = "medibook.payment.queue";
    public static final String FOLLOWUP_QUEUE      = "medibook.followup.queue";
    public static final String BULK_QUEUE          = "medibook.bulk.queue";

    // Dead Letter Queue names — one per main queue
    public static final String BOOKING_DLQ      = "medibook.booking.dlq";
    public static final String CANCELLATION_DLQ = "medibook.cancellation.dlq";
    public static final String REMINDER_DLQ     = "medibook.reminder.dlq";
    public static final String PAYMENT_DLQ      = "medibook.payment.dlq";

    // ── Routing keys ───────────────────────────────────────────────────────────
    // Producers (appointment-service, payment-service etc.) publish to the exchange
    // using these routing keys. The bindings below connect keys to queues.
    public static final String BOOKING_KEY      = "appointment.booking.#";
    public static final String CANCELLATION_KEY = "appointment.cancelled";
    public static final String REMINDER_KEY     = "appointment.reminder.#";
    public static final String PAYMENT_KEY      = "payment.#";
    public static final String FOLLOWUP_KEY     = "medical.followup";
    public static final String BULK_KEY         = "admin.bulk.#";

    // ── Exchange ───────────────────────────────────────────────────────────────

    /** Topic exchange — routes messages using pattern-matching routing keys */
    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder
                .topicExchange(NOTIFICATION_EXCHANGE)
                .durable(true)   // survives RabbitMQ restart
                .build();
    }

    /** Dead Letter Exchange — receives messages that failed processing */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    // ── Queues ─────────────────────────────────────────────────────────────────

    /**
     * Helper: creates a durable queue with a DLQ attached.
     * durable = true means the queue survives a RabbitMQ restart.
     * x-dead-letter-exchange = where to route messages that fail 3 times.
     * x-dead-letter-routing-key = routing key used on the DLX.
     */
    private Queue createQueueWithDLQ(String queueName, String dlqName) {
        return QueueBuilder
                .durable(queueName)
                .withArgument("x-dead-letter-exchange",    DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean public Queue bookingQueue()      { return createQueueWithDLQ(BOOKING_QUEUE,      BOOKING_DLQ); }
    @Bean public Queue cancellationQueue() { return createQueueWithDLQ(CANCELLATION_QUEUE, CANCELLATION_DLQ); }
    @Bean public Queue reminderQueue()     { return createQueueWithDLQ(REMINDER_QUEUE,     REMINDER_DLQ); }
    @Bean public Queue paymentQueue()      { return createQueueWithDLQ(PAYMENT_QUEUE,      PAYMENT_DLQ); }
    @Bean public Queue followupQueue()     { return QueueBuilder.durable(FOLLOWUP_QUEUE).build(); }
    @Bean public Queue bulkQueue()         { return QueueBuilder.durable(BULK_QUEUE).build(); }

    // Dead letter queues — plain durable queues, no further DLQ chaining
    @Bean public Queue bookingDLQ()      { return QueueBuilder.durable(BOOKING_DLQ).build(); }
    @Bean public Queue cancellationDLQ() { return QueueBuilder.durable(CANCELLATION_DLQ).build(); }
    @Bean public Queue reminderDLQ()     { return QueueBuilder.durable(REMINDER_DLQ).build(); }
    @Bean public Queue paymentDLQ()      { return QueueBuilder.durable(PAYMENT_DLQ).build(); }

    // ── Bindings ───────────────────────────────────────────────────────────────
    // Bindings connect queues to the exchange via routing key patterns.
    // "#" matches zero or more words. "*" matches exactly one word.

    @Bean
    public Binding bookingBinding(Queue bookingQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(bookingQueue).to(notificationExchange).with(BOOKING_KEY);
    }

    @Bean
    public Binding cancellationBinding(Queue cancellationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(cancellationQueue).to(notificationExchange).with(CANCELLATION_KEY);
    }

    @Bean
    public Binding reminderBinding(Queue reminderQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(reminderQueue).to(notificationExchange).with(REMINDER_KEY);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(paymentQueue).to(notificationExchange).with(PAYMENT_KEY);
    }

    @Bean
    public Binding followupBinding(Queue followupQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(followupQueue).to(notificationExchange).with(FOLLOWUP_KEY);
    }

    @Bean
    public Binding bulkBinding(Queue bulkQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(bulkQueue).to(notificationExchange).with(BULK_KEY);
    }

    // DLQ bindings to DLX
    @Bean
    public Binding bookingDLQBinding(Queue bookingDLQ, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(bookingDLQ).to(deadLetterExchange).with(BOOKING_DLQ);
    }

    @Bean
    public Binding cancellationDLQBinding(Queue cancellationDLQ, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(cancellationDLQ).to(deadLetterExchange).with(CANCELLATION_DLQ);
    }

    // ── Message Converter ──────────────────────────────────────────────────────

    /**
     * Use JSON for all messages instead of Java serialization.
     * Reason: JSON messages can be read by any language (Python, Node.js, etc.)
     * and are human-readable in the RabbitMQ Management UI.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure RabbitTemplate to use JSON converter.
     * RabbitTemplate is the class used to PUBLISH messages to RabbitMQ.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}