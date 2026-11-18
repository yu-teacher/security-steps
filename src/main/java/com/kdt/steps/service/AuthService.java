package com.kdt.steps.service;

import com.kdt.steps.auth.JwtService;
import com.kdt.steps.auth.PasswordAuthenticationService;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.RefreshRequest;
import com.kdt.steps.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordAuthenticationService passwordAuthenticationService;
    private final JwtService jwtService;

    public TokenResponse issueTokens(LoginRequest request) {
        return tokensFor(passwordAuthenticationService.authenticateLoginId(request));
    }

    public TokenResponse refresh(RefreshRequest request) {
        return tokensFor(jwtService.parseRefresh(request.refreshToken()).getSubject());
    }

    private TokenResponse tokensFor(String loginId) {
        return new TokenResponse(jwtService.createAccess(loginId), jwtService.createRefresh(loginId));
    }
}
