package com.medibook.review.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for provider-service.
 * Replaces the RestTemplate call in ReviewServiceImpl.pushAvgRatingToProvider():
 *   PUT /api/v1/providers/{providerId}/rating
 */
@FeignClient(name = "provider-service")
public interface ProviderServiceClient {

    @PutMapping("/api/v1/providers/{providerId}/rating")
    void updateProviderRating(@PathVariable("providerId") Long providerId,
                              @RequestBody Map<String, Object> payload);
}
