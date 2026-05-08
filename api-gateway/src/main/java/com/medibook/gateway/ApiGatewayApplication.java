package com.medibook.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MediBook API Gateway — port 8080
 *
 * What it does:
 *   1. Receives every HTTP request from clients (browser, mobile, Postman)
 *   2. JwtAuthFilter validates the Bearer token on protected routes
 *   3. Routes the request to the correct downstream microservice
 *   4. Returns the service response back to the client
 *
 * NOTE: Uses Spring Cloud Gateway (WebFlux / reactive).
 *       Do NOT add spring-boot-starter-web — it conflicts.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
