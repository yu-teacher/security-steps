package com.kdt.steps.controller;

import com.kdt.steps.dto.PostResponse;
import com.kdt.steps.dto.PostWriteRequest;
import com.kdt.steps.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostApiController {
    private final PostService postService;

    @GetMapping
    public List<PostResponse> list() {
        return postService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal String loginId, @Valid @RequestBody PostWriteRequest request) {
        return postService.create(loginId, request);
    }

    @PutMapping("/{id}")
    public PostResponse update(@AuthenticationPrincipal String loginId, @PathVariable Long id,
                               @Valid @RequestBody PostWriteRequest request) {
        return postService.update(loginId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String loginId, @PathVariable Long id) {
        postService.delete(loginId, id);
    }
}
