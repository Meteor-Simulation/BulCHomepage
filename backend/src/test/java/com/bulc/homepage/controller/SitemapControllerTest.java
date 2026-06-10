package com.bulc.homepage.controller;

import com.bulc.homepage.entity.Post;
import com.bulc.homepage.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SitemapControllerTest {

    private PostRepository postRepository;
    private SitemapController sitemapController;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        sitemapController = new SitemapController(postRepository);
        ReflectionTestUtils.setField(sitemapController, "frontendBaseUrl", "https://bulc.msimul.com");
    }

    @Test
    @DisplayName("공개 게시글을 sitemap XML로 반환한다")
    void postsSitemap_returnsPublicPostUrls() {
        Post post = Post.builder()
                .authorId(UUID.randomUUID())
                .title("테스트 게시글")
                .visibility("PUBLIC")
                .updatedAt(LocalDateTime.of(2026, 6, 10, 12, 0))
                .build();
        ReflectionTestUtils.setField(post, "id", 42L);
        when(postRepository.findTop1000ByIsDeletedFalseAndVisibilityOrderByUpdatedAtDesc(anyString()))
                .thenReturn(List.of(post));

        String xml = sitemapController.postsSitemap();

        assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<loc>https://bulc.msimul.com/board/42</loc>");
        assertThat(xml).contains("<lastmod>2026-06-10</lastmod>");
        assertThat(xml).contains("</urlset>");
    }

    @Test
    @DisplayName("게시글이 없으면 빈 urlset을 반환한다")
    void postsSitemap_emptyWhenNoPosts() {
        when(postRepository.findTop1000ByIsDeletedFalseAndVisibilityOrderByUpdatedAtDesc(anyString()))
                .thenReturn(List.of());

        String xml = sitemapController.postsSitemap();

        assertThat(xml).contains("<urlset");
        assertThat(xml).doesNotContain("<loc>");
    }
}
