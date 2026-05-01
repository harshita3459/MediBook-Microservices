package com.medibook.schedule.config;

import com.medibook.schedule.util.JwtUtil;
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
                // Public — patients browse available slots without logging in
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/slots/available",
                    "/api/v1/slots/available/range",
                    "/api/v1/slots/{id}"
                ).permitAll()
                // Internal — appointment-service calls book/release without user token
                .requestMatchers("/api/v1/slots/*/book",
                                 "/api/v1/slots/*/release").permitAll()
                // Swagger + actuator
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                 "/swagger-ui.html", "/actuator/health").permitAll()
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
        String gatewayRole = req.getHeader("X-User-Role");
        if (gatewayRole != null && !gatewayRole.isBlank()) {
            var auth = new UsernamePasswordAuthenticationToken(
                req.getHeader("X-Authenticated-User"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + gatewayRole)));
            String gatewayUserId = req.getHeader("X-User-Id");
            if (gatewayUserId != null && !gatewayUserId.isBlank()) {
                auth.setDetails(Long.valueOf(gatewayUserId));
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    String email  = jwtUtil.extractEmail(token);
                    String role   = jwtUtil.extractRole(token);
                    Long   userId = jwtUtil.extractUserId(token);
                    var auth = new UsernamePasswordAuthenticationToken(
                        email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    auth.setDetails(userId);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
