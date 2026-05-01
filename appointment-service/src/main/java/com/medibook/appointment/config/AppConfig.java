package com.medibook.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate bean — used by AppointmentServiceImpl to call
     * schedule-service, payment-service, and notification-service.
     * Declared here so it can be easily mocked in unit tests.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}