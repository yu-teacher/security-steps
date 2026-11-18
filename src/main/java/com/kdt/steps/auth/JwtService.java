package com.kdt.steps.auth;

import com.kdt.steps.exception.TokenFailedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;
    private final long accessSeconds;

    public JwtService(@Value("${kdt.jwt.secret}") String secret,
                      @Value("${kdt.jwt.access-seconds}") long accessSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = accessSeconds;
    }

    public String createAccess(String loginId) {
        Instant now = Instant.now();
        return Jwts.builder()
                   .subject(loginId)
                   .issuedAt(Date.from(now))
                   .expiration(Date.from(now.plusSeconds(accessSeconds)))
                   .signWith(key, Jwts.SIG.HS256)
                   .compact();
    }

    public Claims parseAccess(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenFailedException();
        }
    }

    public String bearerValue(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new TokenFailedException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new TokenFailedException();
        }
        return token;
    }
}
