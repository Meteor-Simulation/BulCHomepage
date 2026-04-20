package com.bulc.homepage.dto.response;

import com.bulc.homepage.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostListResponse {

    private Long id;
    private String title;
    private String authorName;
    private String visibility;
    private Integer viewCount;
    private LocalDateTime createdAt;

    public static PostListResponse from(Post post, String authorName) {
        return PostListResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .authorName(authorName)
                .visibility(post.getVisibility())
                .viewCount(post.getViewCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
