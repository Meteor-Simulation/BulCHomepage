package com.bulc.homepage.controller;

import com.bulc.homepage.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final EmailService emailService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "BulC Homepage Backend is running!");
        return ResponseEntity.ok(response);
    }

    /**
     * 이메일 서비스 진단
     * 브라우저에서 /api/health/email 로 접속하면 설정 상태를 확인할 수 있음
     */
    /**
     * 헬스 체크 알림 이메일 발송 (서버 내부 cron에서 호출)
     */
    @PostMapping("/health/alert")
    public ResponseEntity<Map<String, Object>> healthAlert(@RequestBody Map<String, String> request) {
        String to = request.get("to");
        String subject = request.get("subject");
        String body = request.get("body");

        if (to == null || subject == null || body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "to, subject, body 필수"));
        }

        try {
            emailService.sendHtmlEmail(to, subject, body.replace("\n", "<br>"));
            log.info("헬스 체크 알림 발송 완료 — to: {}", to);
            return ResponseEntity.ok(Map.of("status", "sent"));
        } catch (Exception e) {
            log.error("헬스 체크 알림 발송 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health/email")
    public ResponseEntity<Map<String, Object>> emailHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("email", emailService.getDiagnostics());
        return ResponseEntity.ok(response);
    }
}
