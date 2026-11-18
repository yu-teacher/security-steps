package com.kdt.steps.dto;

import com.kdt.steps.domain.Post;

public record PostResponse(
        Long id,
        String title,
        String body,
        String loginId
) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getBody(), post.getMember().getLoginId());
    }
}
