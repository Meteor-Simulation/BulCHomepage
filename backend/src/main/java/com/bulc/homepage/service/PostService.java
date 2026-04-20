package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.PostRequest;
import com.bulc.homepage.dto.response.PostDetailResponse;
import com.bulc.homepage.dto.response.PostListResponse;
import com.bulc.homepage.entity.Post;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.PostRepository;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private static final List<String> VALID_VISIBILITIES = List.of("PUBLIC", "MEMBER", "STAFF");

    /**
     * 게시글 목록 조회 (모든 등급에서 접근 가능)
     */
    @Transactional(readOnly = true)
    public Page<PostListResponse> getPosts(String search, Pageable pageable) {
        Page<Post> posts;
        if (search != null && !search.isBlank()) {
            posts = postRepository.searchByTitle(search.trim(), pageable);
        } else {
            posts = postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        }

        return posts.map(post -> {
            String authorName = getAuthorName(post.getAuthorId());
            return PostListResponse.from(post, authorName);
        });
    }

    /**
     * 게시글 상세 조회 (등급에 따라 본문 공개/제한)
     */
    @Transactional
    public PostDetailResponse getPost(Long id, String requesterRolesCode) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (post.getIsDeleted()) {
            throw new RuntimeException("삭제된 게시글입니다.");
        }

        String authorName = getAuthorName(post.getAuthorId());
        int requesterLevel = getRoleLevel(requesterRolesCode);
        int requiredLevel = getVisibilityLevel(post.getVisibility());

        if (requesterLevel < requiredLevel) {
            String message = getRestrictedMessage(post.getVisibility());
            return PostDetailResponse.restricted(post, authorName, message);
        }

        // 조회수 증가
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        return PostDetailResponse.full(post, authorName);
    }

    /**
     * 게시글 작성
     */
    @Transactional
    public PostDetailResponse createPost(PostRequest request, UUID authorId) {
        validateVisibility(request.getVisibility());

        Post post = Post.builder()
                .authorId(authorId)
                .title(request.getTitle())
                .contentHtml(request.getContentHtml())
                .visibility(request.getVisibility())
                .build();

        post = postRepository.save(post);
        String authorName = getAuthorName(authorId);

        log.info("게시글 작성 - id: {}, author: {}, visibility: {}", post.getId(), authorId, post.getVisibility());
        return PostDetailResponse.full(post, authorName);
    }

    /**
     * 게시글 수정 (본인 or 스태프)
     */
    @Transactional
    public PostDetailResponse updatePost(Long id, PostRequest request, UUID requesterId, String requesterRolesCode) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (post.getIsDeleted()) {
            throw new RuntimeException("삭제된 게시글입니다.");
        }

        if (!canModify(post, requesterId, requesterRolesCode)) {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        validateVisibility(request.getVisibility());

        post.setTitle(request.getTitle());
        post.setContentHtml(request.getContentHtml());
        post.setVisibility(request.getVisibility());
        post = postRepository.save(post);

        String authorName = getAuthorName(post.getAuthorId());
        log.info("게시글 수정 - id: {}, by: {}", id, requesterId);
        return PostDetailResponse.full(post, authorName);
    }

    /**
     * 게시글 삭제 — 소프트 삭제 (본인 or 스태프)
     */
    @Transactional
    public void deletePost(Long id, UUID requesterId, String requesterRolesCode) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (!canModify(post, requesterId, requesterRolesCode)) {
            throw new RuntimeException("삭제 권한이 없습니다.");
        }

        post.setIsDeleted(true);
        postRepository.save(post);
        log.info("게시글 삭제 - id: {}, by: {}", id, requesterId);
    }

    // ========== 헬퍼 메서드 ==========

    private boolean canModify(Post post, UUID requesterId, String rolesCode) {
        // 스태프(admin/manager)는 모든 글 관리 가능
        if (isStaff(rolesCode)) return true;
        // 본인 글만 수정/삭제 가능
        return post.getAuthorId().equals(requesterId);
    }

    private boolean isStaff(String rolesCode) {
        return "000".equals(rolesCode) || "001".equals(rolesCode);
    }

    /**
     * 역할 코드 → 등급 레벨
     * guest=0, user=1, staff=2
     */
    private int getRoleLevel(String rolesCode) {
        if (rolesCode == null) return 0; // guest
        return switch (rolesCode) {
            case "000", "001" -> 2; // admin, manager → staff
            case "002" -> 1;       // user
            default -> 0;          // guest
        };
    }

    /**
     * visibility → 필요 등급 레벨
     */
    private int getVisibilityLevel(String visibility) {
        return switch (visibility) {
            case "STAFF" -> 2;
            case "MEMBER" -> 1;
            default -> 0; // PUBLIC
        };
    }

    private String getRestrictedMessage(String visibility) {
        return switch (visibility) {
            case "STAFF" -> "스태프에게만 공개된 게시글입니다.";
            case "MEMBER" -> "회원에게만 공개된 게시글입니다.";
            default -> "";
        };
    }

    private void validateVisibility(String visibility) {
        if (!VALID_VISIBILITIES.contains(visibility)) {
            throw new RuntimeException("유효하지 않은 공개 범위입니다: " + visibility);
        }
    }

    private String getAuthorName(UUID authorId) {
        return userRepository.findById(authorId)
                .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                .orElse("알 수 없음");
    }
}
