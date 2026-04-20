package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.PostRequest;
import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.dto.response.PostDetailResponse;
import com.bulc.homepage.dto.response.PostListResponse;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;

    /**
     * 게시글 목록 조회 (인증 불필요)
     */
    @GetMapping
    public ResponseEntity<Page<PostListResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<PostListResponse> posts = postService.getPosts(search, pageable);
        return ResponseEntity.ok(posts);
    }

    /**
     * 게시글 상세 조회 (등급에 따라 본문 공개/제한)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(@PathVariable Long id) {
        String rolesCode = getCurrentUserRolesCode();
        PostDetailResponse post = postService.getPost(id, rolesCode);
        return ResponseEntity.ok(ApiResponse.success("게시글 조회 성공", post));
    }

    /**
     * 게시글 작성 (인증 필수)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PostDetailResponse>> createPost(
            @Valid @RequestBody PostRequest request) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }
        PostDetailResponse post = postService.createPost(request, userId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 작성되었습니다.", post));
    }

    /**
     * 게시글 수정 (본인 or 스태프)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request) {
        UUID userId = getCurrentUserId();
        String rolesCode = getCurrentUserRolesCode();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }
        PostDetailResponse post = postService.updatePost(id, request, userId, rolesCode);
        return ResponseEntity.ok(ApiResponse.success("게시글이 수정되었습니다.", post));
    }

    /**
     * 게시글 삭제 (본인 or 스태프)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long id) {
        UUID userId = getCurrentUserId();
        String rolesCode = getCurrentUserRolesCode();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }
        postService.deletePost(id, userId, rolesCode);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다.", null));
    }

    // ========== 헬퍼 ==========

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUserRolesCode() {
        UUID userId = getCurrentUserId();
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getRolesCode)
                .orElse(null);
    }
}
