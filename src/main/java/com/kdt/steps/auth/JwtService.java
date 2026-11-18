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
import java.util.UUID;

@Component
public class JwtService {
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_TYPE = "tokenType";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtService(@Value("${kdt.jwt.secret}") String secret,
                      @Value("${kdt.jwt.access-seconds}") long accessSeconds,
                      @Value("${kdt.jwt.refresh-seconds}") long refreshSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    public String createAccess(String loginId) {
        return compact(loginId, TYPE_ACCESS, accessSeconds);
    }

    public String createRefresh(String loginId) {
        return compact(loginId, TYPE_REFRESH, refreshSeconds);
    }

    public Claims parseAccess(String token) {
        return parseWithType(token, TYPE_ACCESS);
    }

    public Claims parseRefresh(String token) {
        return parseWithType(token, TYPE_REFRESH);
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

    private String compact(String loginId, String tokenType, long seconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                   .subject(loginId)
                   .id(UUID.randomUUID().toString())
                   .claim(CLAIM_TYPE, tokenType)
                   .issuedAt(Date.from(now))
                   .expiration(Date.from(now.plusSeconds(seconds)))
                   .signWith(key, Jwts.SIG.HS256)
                   .compact();
    }

    private Claims parseWithType(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new TokenFailedException();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenFailedException();
        }
    }
}
