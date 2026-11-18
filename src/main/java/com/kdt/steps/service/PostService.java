package com.kdt.steps.service;

import com.kdt.steps.domain.Post;
import com.kdt.steps.dto.PostResponse;
import com.kdt.steps.dto.PostWriteRequest;
import com.kdt.steps.exception.NotFoundException;
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

    public List<PostResponse> list() {
        return postRepository.findAll(Sort.by("id")).stream().map(PostResponse::from).toList();
    }

    @Transactional
    public PostResponse create(PostWriteRequest request) {
        Post post = Post.builder()
                        .author(request.author())
                        .title(request.title())
                        .body(request.body())
                        .build();
        return PostResponse.from(postRepository.save(post));
    }

    @Transactional
    public PostResponse update(Long id, PostWriteRequest request) {
        Post post = find(id);
        post.edit(request.title(), request.body());
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(find(id));
    }

    private Post find(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new NotFoundException("post missing"));
    }
}
