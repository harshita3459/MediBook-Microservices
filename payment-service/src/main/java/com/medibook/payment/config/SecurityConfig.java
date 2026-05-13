package com.medibook.payment.config;

import com.medibook.payment.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * When behind the gateway, the JWT has already been validated.
 * The gateway forwards:
 *   X-Authenticated-User  — email
 *   X-User-Role           — PATIENT / PROVIDER / ADMIN
 *   X-User-Id             — userId
 *
 * The filter now accepts EITHER:
 *   Bearer JWT directly (Postman / direct calls without gateway)
 *   X-User-Role header from gateway (gateway-routed calls)
 *
 * Both paths produce a valid SecurityContext so @PreAuthorize works in both modes.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.PUT, "/api/v1/payments/*/status").permitAll()
                .requestMatchers(
                    "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        // ── Path A: Gateway forwarded headers (preferred) ──────────────────
        String gatewayRole  = req.getHeader("X-User-Role");
        String gatewayEmail = req.getHeader("X-Authenticated-User");

        if (gatewayRole != null && !gatewayRole.isBlank()) {
            var auth = new UsernamePasswordAuthenticationToken(
                gatewayEmail, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + gatewayRole)));
            String gatewayUserId = req.getHeader("X-User-Id");
            if (gatewayUserId != null && !gatewayUserId.isBlank()) {
                auth.setDetails(Long.valueOf(gatewayUserId));
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(req, res);
            return;
        }

        // ── Path B: Direct JWT (Postman / development without gateway) ─────
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                        jwtUtil.extractEmail(token), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + jwtUtil.extractRole(token))));
                    auth.setDetails(jwtUtil.extractUserId(token));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
            }
        }

        chain.doFilter(req, res);
    }
}
