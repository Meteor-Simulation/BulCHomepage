package com.bulc.homepage.controller;

import com.bulc.homepage.entity.Post;
import com.bulc.homepage.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 검색엔진용 동적 사이트맵.
 *
 * 게시판 게시글(공개 글)의 URL 목록을 sitemap XML로 제공한다.
 * 프론트엔드(robots.txt)에서 크로스 호스트 사이트맵으로 참조된다:
 *   Sitemap: https://api.msimul.com/sitemap/posts.xml
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final DateTimeFormatter LASTMOD_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PostRepository postRepository;

    /** 사이트맵에 표기할 프론트엔드 기본 URL */
    @Value("${app.frontend-base-url:https://bulc.msimul.com}")
    private String frontendBaseUrl;

    @GetMapping(value = "/sitemap/posts.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String postsSitemap() {
        List<Post> posts = postRepository
                .findTop1000ByIsDeletedFalseAndVisibilityOrderByUpdatedAtDesc("PUBLIC");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Post post : posts) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(frontendBaseUrl).append("/board/").append(post.getId()).append("</loc>\n");
            if (post.getUpdatedAt() != null) {
                xml.append("    <lastmod>").append(post.getUpdatedAt().format(LASTMOD_FORMAT)).append("</lastmod>\n");
            }
            xml.append("  </url>\n");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }
}
