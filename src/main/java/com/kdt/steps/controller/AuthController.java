package com.kdt.steps.controller;

import com.kdt.steps.auth.CookieAuthWriter;
import com.kdt.steps.dto.AccessTokenResponse;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MeResponse;
import com.kdt.steps.dto.RefreshRequest;
import com.kdt.steps.dto.TokenResponse;
import com.kdt.steps.exception.TokenFailedException;
import com.kdt.steps.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CookieAuthWriter cookieAuthWriter;

    @PostMapping("/token")
    public TokenResponse issueTokens(@Valid @RequestBody LoginRequest request) {
        return authService.issueTokens(request);
    }

    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/token-cookie")
    public AccessTokenResponse tokenCookie(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenResponse tokens = authService.issueTokens(request);
        cookieAuthWriter.writeRefresh(response, tokens.refreshToken());
        return new AccessTokenResponse(tokens.accessToken());
    }

    @PostMapping("/token/refresh-cookie")
    public AccessTokenResponse refreshCookie(
            @CookieValue(name = CookieAuthWriter.REFRESH, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new TokenFailedException();
        }
        TokenResponse tokens = authService.refresh(new RefreshRequest(refreshToken));
        cookieAuthWriter.writeRefresh(response, tokens.refreshToken());
        return new AccessTokenResponse(tokens.accessToken());
    }

    @PostMapping("/logout-cookie")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutCookie(@CookieValue(name = CookieAuthWriter.REFRESH, required = false) String refreshToken,
                             HttpServletResponse response) {
        authService.logout(refreshToken);
        cookieAuthWriter.clearRefresh(response);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal String loginId) {
        return new MeResponse(loginId);
    }
}
