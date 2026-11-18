package com.kdt.steps.controller;

import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MemberResponse;
import com.kdt.steps.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public MemberResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
