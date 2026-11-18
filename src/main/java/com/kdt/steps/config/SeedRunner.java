package com.kdt.steps.config;

import com.kdt.steps.domain.Member;
import com.kdt.steps.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.count() > 0) {
            return;
        }
        save("kdt01", "김코딩");
        save("kdt02", "박해커");
    }

    private void save(String loginId, String nickname) {
        memberRepository.save(Member.builder()
                                    .loginId(loginId)
                                    .password(passwordEncoder.encode("kdt1234"))
                                    .nickname(nickname)
                                    .build());
    }
}
