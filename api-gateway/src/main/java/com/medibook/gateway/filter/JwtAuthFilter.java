package com.medibook.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * JwtAuthFilter — Spring Cloud Gateway named filter factory.
 *
 * Applied to protected routes in application.yml using:
 *   filters:
 *     - name: JwtAuthFilter
 *
 * Flow:
 *   1. Check Authorization: Bearer <token> header is present
 *   2. Validate the JWT (signature, expiry) using JwtUtil
 *   3. If valid: extract email, role, userId → add as request headers → forward downstream
 *   4. If invalid: return 401 JSON immediately — request never reaches the service
 *
 * Downstream services receive:
 *   X-Authenticated-User  : email from JWT
 *   X-User-Role           : role (PATIENT / PROVIDER / ADMIN)
 *   X-User-Id             : userId (Long)
 *
 * This means downstream services DON'T need to re-validate JWT —
 * they trust these headers because only the gateway can set them.
 */
@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or malformed Authorization header for path: {}", path);
                return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Authorization header missing. Please include: Authorization: Bearer <your-jwt-token>");
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.isValid(token)) {
                log.warn("Invalid or expired JWT for path: {}", path);
                return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token. Please login again to get a fresh token.");
            }

            String email  = jwtUtil.extractEmail(token);
            String role   = jwtUtil.extractRole(token);
            Long   userId = jwtUtil.extractUserId(token);

            log.debug("JWT valid — user={} role={} path={}", email, role, path);

            // Mutate request — add identity headers for downstream services
            ServerHttpRequest mutated = request.mutate()
                    .header("X-Authenticated-User", email)
                    .header("X-User-Role", role != null ? role : "")
                    .header("X-User-Id", userId != null ? userId.toString() : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
            "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
            LocalDateTime.now(), status.value(), status.getReasonPhrase(), message
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        // No config fields needed — filter uses only JWT secret from application.yml
    }
}
