package com.kdt.steps.config;

import com.kdt.steps.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

@Configuration
public class CookieOriginConfig implements WebMvcConfigurer {
    private static final Set<String> ALLOWED =
            Set.of("http://127.0.0.1:8080", "http://localhost:8080", "http://127.0.0.1:8000");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        if (!"POST".equalsIgnoreCase(request.getMethod())) {
                            return true;
                        }
                        String origin = request.getHeader("Origin");
                        if (origin == null || !ALLOWED.contains(origin)) {
                            throw new ForbiddenException("origin not allowed");
                        }
                        return true;
                    }
                })
                .addPathPatterns(
                        "/api/v1/auth/token-cookie",
                        "/api/v1/auth/token/refresh-cookie",
                        "/api/v1/auth/logout-cookie");
    }
}
