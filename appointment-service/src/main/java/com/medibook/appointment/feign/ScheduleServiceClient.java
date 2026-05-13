package com.medibook.appointment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "schedule-service")
public interface ScheduleServiceClient {

    @GetMapping("/api/v1/slots/{id}")
    Map<String, Object> getSlotById(@PathVariable("id") Long slotId);

    @PutMapping("/api/v1/slots/{id}/book")
    void bookSlot(@PathVariable("id") Long slotId, @RequestParam("appointmentId") Long appointmentId);

    @PutMapping("/api/v1/slots/{id}/release")
    void releaseSlot(@PathVariable("id") Long slotId);
}