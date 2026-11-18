package com.kdt.steps.service;

import com.kdt.steps.auth.PasswordAuthenticationService;
import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MemberResponse;
import com.kdt.steps.exception.LoginFailedException;
import com.kdt.steps.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordAuthenticationService passwordAuthenticationService;

    public MemberResponse login(LoginRequest request) {
        String loginId = passwordAuthenticationService.authenticateLoginId(request);
        return memberRepository.findByLoginId(loginId).map(MemberResponse::from).orElseThrow(LoginFailedException::new);
    }
}
