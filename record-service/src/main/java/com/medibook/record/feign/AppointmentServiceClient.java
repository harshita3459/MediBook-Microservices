package com.medibook.record.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "appointment-service")
public interface AppointmentServiceClient {

    @GetMapping("/api/v1/appointments/{id}")
    Map<String, Object> getAppointmentById(@PathVariable("id") Long appointmentId);
}