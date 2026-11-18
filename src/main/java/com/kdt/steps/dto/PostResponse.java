package com.kdt.steps.dto;

import com.kdt.steps.domain.Post;

public record PostResponse(
        Long id,
        String author,
        String title,
        String body
) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getAuthor(), post.getTitle(), post.getBody());
    }
}
