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
public class PostDetailResponse {

    private Long id;
    private String title;
    private String contentHtml;
    private String authorId;
    private String authorName;
    private String visibility;
    private Integer viewCount;
    private boolean restricted;
    private String restrictedMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostDetailResponse full(Post post, String authorName) {
        return PostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .contentHtml(post.getContentHtml())
                .authorId(post.getAuthorId().toString())
                .authorName(authorName)
                .visibility(post.getVisibility())
                .viewCount(post.getViewCount())
                .restricted(false)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    public static PostDetailResponse restricted(Post post, String authorName, String message) {
        return PostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .authorName(authorName)
                .visibility(post.getVisibility())
                .viewCount(post.getViewCount())
                .restricted(true)
                .restrictedMessage(message)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
