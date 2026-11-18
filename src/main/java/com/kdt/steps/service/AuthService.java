package com.kdt.steps.service;

import com.kdt.steps.dto.LoginRequest;
import com.kdt.steps.dto.MemberResponse;
import com.kdt.steps.exception.LoginFailedException;
import com.kdt.steps.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberResponse login(LoginRequest request) {
        return memberRepository.findByLoginId(request.loginId())
                               .filter(member -> passwordEncoder.matches(request.password(), member.getPassword()))
                               .map(MemberResponse::from)
                               .orElseThrow(LoginFailedException::new);
    }
}
