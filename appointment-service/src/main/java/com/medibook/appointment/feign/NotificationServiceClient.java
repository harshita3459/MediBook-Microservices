package com.medibook.appointment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications/appointment-event")
    void sendAppointmentEvent(@RequestBody Map<String, Object> payload);
}