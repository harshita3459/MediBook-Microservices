package com.medibook.appointment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "provider-service")
public interface ProviderServiceClient {

    @GetMapping("/api/v1/providers/{id}")
    Map<String, Object> getProviderById(@PathVariable("id") Long providerId);
}