package com.kdt.steps.auth;

import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.exception.LoginFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PasswordAuthenticationService {
    private static final int BCRYPT_MAX_BYTES = 72;
    private final AuthenticationManager authenticationManager;

    public String authenticateLoginId(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new LoginFailedException();
        }
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.loginId(), request.password())).getName();
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            throw new LoginFailedException();
        }
    }
}
