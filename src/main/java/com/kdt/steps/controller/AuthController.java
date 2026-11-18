package com.kdt.steps.controller;

import com.kdt.steps.auth.SessionStore;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MemberResponse;
import com.kdt.steps.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    public static final String SID = "SID";

    private final AuthService authService;
    private final SessionStore sessionStore;

    @PostMapping("/login")
    public MemberResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        MemberResponse member = authService.login(request);
        writeSid(response, sessionStore.create(member.loginId()), Duration.ofMinutes(30));
        return member;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = SID, required = false) String sid, HttpServletResponse response) {
        sessionStore.remove(sid);
        writeSid(response, "", Duration.ZERO);
    }

    private void writeSid(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(SID, value).httpOnly(true).path("/").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
