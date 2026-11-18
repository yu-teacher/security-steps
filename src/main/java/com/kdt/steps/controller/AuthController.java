package com.kdt.steps.controller;

import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MeResponse;
import com.kdt.steps.dto.TokenResponse;
import com.kdt.steps.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/token")
    public TokenResponse issueTokens(@Valid @RequestBody LoginRequest request) {
        return authService.issueTokens(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal String loginId) {
        return new MeResponse(loginId);
    }
}
