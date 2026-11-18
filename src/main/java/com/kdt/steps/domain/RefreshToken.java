package com.kdt.steps.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Builder
    private RefreshToken(String jti, String loginId, Instant expiresAt) {
        this.jti = jti;
        this.loginId = loginId;
        this.expiresAt = expiresAt;
    }

    public void revoke() {
        this.revoked = true;
    }
}
