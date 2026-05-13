package com.medibook.review.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign client for appointment-service.
 * Replaces the RestTemplate call in ReviewServiceImpl.verifyAppointmentCompleted():
 *   GET /api/v1/appointments/{appointmentId}
 */
@FeignClient(name = "appointment-service")
public interface AppointmentServiceClient {

    @GetMapping("/api/v1/appointments/{appointmentId}")
    Map<String, Object> getAppointment(@PathVariable("appointmentId") Long appointmentId);
}
