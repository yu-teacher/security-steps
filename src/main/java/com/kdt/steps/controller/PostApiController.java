package com.kdt.steps.controller;

import com.kdt.steps.auth.SessionStore;
import com.kdt.steps.dto.PostResponse;
import com.kdt.steps.dto.PostWriteRequest;
import com.kdt.steps.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostApiController {
    private final PostService postService;
    private final SessionStore sessionStore;

    @GetMapping
    public List<PostResponse> list() {
        return postService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@CookieValue(name = AuthController.SID, required = false) String sid,
                               @Valid @RequestBody PostWriteRequest request) {
        return postService.create(sessionStore.loginId(sid), request);
    }

    @PutMapping("/{id}")
    public PostResponse update(@CookieValue(name = AuthController.SID, required = false) String sid,
                               @PathVariable Long id, @Valid @RequestBody PostWriteRequest request) {
        return postService.update(sessionStore.loginId(sid), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CookieValue(name = AuthController.SID, required = false) String sid, @PathVariable Long id) {
        postService.delete(sessionStore.loginId(sid), id);
    }
}
