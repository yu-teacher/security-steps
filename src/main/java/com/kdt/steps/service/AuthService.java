package com.kdt.steps.service;

import com.kdt.steps.auth.JwtService;
import com.kdt.steps.auth.PasswordAuthenticationService;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordAuthenticationService passwordAuthenticationService;
    private final JwtService jwtService;

    public TokenResponse issueTokens(LoginRequest request) {
        return new TokenResponse(jwtService.createAccess(passwordAuthenticationService.authenticateLoginId(request)));
    }
}
