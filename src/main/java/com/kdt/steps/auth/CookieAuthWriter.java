package com.kdt.steps.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieAuthWriter {
    public static final String REFRESH = "refreshToken";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final Duration refreshMaxAge;

    public CookieAuthWriter(@Value("${kdt.jwt.refresh-seconds}") long refreshSeconds) {
        this.refreshMaxAge = Duration.ofSeconds(refreshSeconds);
    }

    public void writeRefresh(HttpServletResponse response, String refreshToken) {
        add(response, refreshCookie(refreshToken, refreshMaxAge));
    }

    public void clearRefresh(HttpServletResponse response) {
        add(response, refreshCookie("", Duration.ZERO));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH, value)
                             .httpOnly(true)
                             .secure(false)
                             .path(REFRESH_PATH)
                             .maxAge(maxAge)
                             .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
