package com.medibook.appointment.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;

/** JWT validation only — tokens are issued by auth-service, validated here */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() { return Keys.hmacShaKeyFor(secret.getBytes()); }

    public boolean validateToken(String token) {
        try { Jwts.parser().verifyWith(key()).build().parseSignedClaims(token); return true; }
        catch (Exception e) { log.warn("Invalid token: {}", e.getMessage()); return false; }
    }

    public String extractEmail(String t)  { return claims(t).getSubject(); }
    public String extractRole(String t)   { return claims(t).get("role", String.class); }
    public Long   extractUserId(String t) {
        Object id = claims(t).get("userId");
        return id != null ? Long.valueOf(id.toString()) : null;
    }

    private Claims claims(String t) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(t).getPayload();
    }
}