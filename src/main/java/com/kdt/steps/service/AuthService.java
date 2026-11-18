package com.kdt.steps.service;

import com.kdt.steps.auth.JwtService;
import com.kdt.steps.auth.PasswordAuthenticationService;
import com.kdt.steps.domain.RefreshToken;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.RefreshRequest;
import com.kdt.steps.dto.TokenResponse;
import com.kdt.steps.exception.TokenFailedException;
import com.kdt.steps.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final PasswordAuthenticationService passwordAuthenticationService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenResponse issueTokens(LoginRequest request) {
        return tokensFor(passwordAuthenticationService.authenticateLoginId(request));
    }

    @Transactional(noRollbackFor = TokenFailedException.class)
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parseRefresh(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByJti(claims.getId())
                                                    .orElseThrow(TokenFailedException::new);
        if (stored.isRevoked()) {
            refreshTokenRepository.findAllByLoginIdAndRevokedFalse(stored.getLoginId())
                                  .forEach(RefreshToken::revoke);
            throw new TokenFailedException();
        }
        stored.revoke();
        return tokensFor(stored.getLoginId());
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            refreshTokenRepository.findByJti(jwtService.parseRefresh(refreshToken).getId())
                                  .ifPresent(RefreshToken::revoke);
        } catch (TokenFailedException ignored) {
        }
    }

    private TokenResponse tokensFor(String loginId) {
        String refresh = jwtService.createRefresh(loginId);
        Claims claims = jwtService.parseRefresh(refresh);
        refreshTokenRepository.save(RefreshToken.builder()
                                                .jti(claims.getId())
                                                .loginId(loginId)
                                                .expiresAt(claims.getExpiration().toInstant())
                                                .build());
        return new TokenResponse(jwtService.createAccess(loginId), refresh);
    }
}
