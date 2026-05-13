package com.medibook.schedule;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for schedule-service.
 * @EnableScheduling activates the SlotCleanupScheduler cron jobs.
 */
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(
        title       = "MediBook — Schedule Service API",
        version     = "1.0",
        description = "Provider availability slot management, booking, blocking and recurring generation"
))
@SecurityScheme(
        name         = "bearerAuth",
        type         = SecuritySchemeType.HTTP,
        scheme       = "bearer",
        bearerFormat = "JWT"
)
public class ScheduleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduleServiceApplication.class, args);
    }
}