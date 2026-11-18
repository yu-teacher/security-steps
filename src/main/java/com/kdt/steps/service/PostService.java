package com.kdt.steps.service;

import com.kdt.steps.domain.Member;
import com.kdt.steps.domain.Post;
import com.kdt.steps.dto.PostResponse;
import com.kdt.steps.dto.PostWriteRequest;
import com.kdt.steps.exception.ForbiddenException;
import com.kdt.steps.exception.NotFoundException;
import com.kdt.steps.exception.TokenFailedException;
import com.kdt.steps.repository.MemberRepository;
import com.kdt.steps.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    public List<PostResponse> list() {
        return postRepository.findAll(Sort.by("id")).stream().map(PostResponse::from).toList();
    }

    @Transactional
    public PostResponse create(String loginId, PostWriteRequest request) {
        Post post = Post.builder()
                        .member(caller(loginId))
                        .title(request.title())
                        .body(request.body())
                        .build();
        return PostResponse.from(postRepository.save(post));
    }

    @Transactional
    public PostResponse update(String loginId, Long id, PostWriteRequest request) {
        Post post = ownPost(loginId, id);
        post.edit(request.title(), request.body());
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(String loginId, Long id) {
        postRepository.delete(ownPost(loginId, id));
    }

    private Member caller(String loginId) {
        return memberRepository.findByLoginId(loginId)
                               .orElseThrow(TokenFailedException::new);
    }

    private Post ownPost(String loginId, Long id) {
        Member caller = caller(loginId);
        Post post = postRepository.findById(id).orElseThrow(() -> new NotFoundException("post missing"));
        if (!post.getMember().getId().equals(caller.getId())) {
            throw new ForbiddenException();
        }
        return post;
    }
}
