package com.kdt.steps.auth;

import com.kdt.steps.domain.Member;
import com.kdt.steps.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        Member member = memberRepository.findByLoginId(username)
                                        .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(member.getLoginId()).password(member.getPassword()).roles("USER").build();
    }
}
