package com.bulc.homepage.controller;

import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.OperationalMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MDP-496 운영성 메일 Admin 트리거 API.
 *
 * 권한: roles_code 000(관리자) / 001(매니저) 만 호출 가능.
 */
@RestController
@RequestMapping("/api/admin/mails")
@RequiredArgsConstructor
public class AdminMailController {

    private static final Set<String> ALLOWED_TEMPLATE_KEYS = Set.of(
            "program_update", "terms_change", "security_notice"
    );

    private final OperationalMailService operationalMailService;
    private final UserRepository userRepository;

    /**
     * 운영성 메일 발송.
     *
     * Body 예시:
     * {
     *   "templateKey": "program_update",   // program_update / terms_change / security_notice
     *   "subject": "[BulC] v1.2.0 업데이트 안내",
     *   "title": "BulC v1.2.0 출시",
     *   "contentHtml": "<p>새 기능 ... </p>",
     *   "recipients": ["foo@bar.com", ...]   // 생략 또는 빈 배열이면 활성 사용자 전체
     * }
     */
    @PostMapping("/operational")
    public ResponseEntity<?> sendOperational(@RequestBody Map<String, Object> body) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        String templateKey = (String) body.get("templateKey");
        if (templateKey == null || !ALLOWED_TEMPLATE_KEYS.contains(templateKey)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "templateKey 는 다음 중 하나여야 합니다",
                    "allowed", ALLOWED_TEMPLATE_KEYS
            ));
        }

        String subject = (String) body.get("subject");
        String title = (String) body.get("title");
        String contentHtml = (String) body.get("contentHtml");
        if (subject == null || subject.isBlank() ||
            title == null || title.isBlank() ||
            contentHtml == null || contentHtml.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "subject / title / contentHtml 모두 필수입니다"
            ));
        }

        Object recipientsRaw = body.get("recipients");
        List<String> recipients;
        if (recipientsRaw == null) {
            recipients = null;
        } else if (recipientsRaw instanceof List<?> list) {
            for (Object e : list) {
                if (e != null && !(e instanceof String)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "recipients 의 모든 요소는 문자열(이메일)이어야 합니다"
                    ));
                }
            }
            List<String> typed = new java.util.ArrayList<>(list.size());
            for (Object e : list) {
                if (e != null) typed.add((String) e);
            }
            recipients = typed;
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "recipients 는 배열이어야 합니다"
            ));
        }

        int count = operationalMailService.sendOperationalNotice(
                title, contentHtml, subject, templateKey, recipients);

        return ResponseEntity.ok(Map.of(
                "templateKey", templateKey,
                "sentCount", count
        ));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        try {
            UUID userId = UUID.fromString(auth.getName());
            return userRepository.findById(userId)
                    .map(user -> "000".equals(user.getRolesCode()) || "001".equals(user.getRolesCode()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
