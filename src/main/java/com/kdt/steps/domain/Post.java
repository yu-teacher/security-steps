package com.kdt.steps.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10000)
    private String body;

    @Builder
    private Post(String author, String title, String body) {
        this.author = author;
        this.title = title;
        this.body = body;
    }

    public void edit(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
