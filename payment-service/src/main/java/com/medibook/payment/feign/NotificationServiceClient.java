package com.medibook.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for notification-service.
 * Replaces the RestTemplate call in PaymentServiceImpl.sendNotificationSafely():
 *   POST /api/v1/notifications/payment-event
 */
@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications/payment-event")
    void sendPaymentEvent(@RequestBody Map<String, Object> payload);
}
