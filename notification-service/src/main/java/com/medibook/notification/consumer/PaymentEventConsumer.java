package com.medibook.notification.consumer;

import com.medibook.notification.config.RabbitMQConfig;
import com.medibook.notification.entity.Notification.NotificationChannel;
import com.medibook.notification.entity.Notification.NotificationType;
import com.medibook.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PaymentEventConsumer — processes payment success and refund events from payment-service.
 * Routing key pattern: "payment.#" → routed to medibook.payment.queue
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    /**
     * Handles payment events published by payment-service.
     * eventType in payload: "PAYMENT_SUCCESS" or "PAYMENT_REFUNDED"
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE)
    public void handlePaymentEvent(Map<String, Object> payload) {
        log.info("RabbitMQ received: PAYMENT event — payload={}", payload);

        try {
            Long   patientId   = getLong(payload,   "patientId");
            Long   paymentId   = getLong(payload,   "paymentId");
            String eventType   = getString(payload, "eventType");
            String amount      = getString(payload, "amount");

            if ("PAYMENT_SUCCESS".equals(eventType)) {
                String message = String.format(
                    "Payment of ₹%s received successfully. Payment ID: %d", amount, paymentId);
                notificationService.send(patientId, NotificationType.PAYMENT_SUCCESS,
                    "Payment Successful", message, NotificationChannel.ALL,
                    paymentId, "PAYMENT");

            } else if ("PAYMENT_REFUNDED".equals(eventType)) {
                String message = String.format(
                    "Refund of ₹%s processed. Payment ID: %d. Will credit in 3-5 business days.",
                    amount, paymentId);
                notificationService.send(patientId, NotificationType.PAYMENT_REFUNDED,
                    "Refund Processed", message, NotificationChannel.ALL,
                    paymentId, "PAYMENT");
            }

        } catch (Exception ex) {
            log.error("Failed to process payment event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process payment event", ex);
        }
    }

    private Long getLong(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v != null ? ((Number) v).longValue() : null;
    }

    private String getString(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v != null ? v.toString() : null;
    }
}