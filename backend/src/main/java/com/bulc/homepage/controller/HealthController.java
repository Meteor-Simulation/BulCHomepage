package com.bulc.homepage.controller;

import com.bulc.homepage.email.EmailCategory;
import com.bulc.homepage.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
     * to는 콤마 구분으로 다중 수신자 지정 가능 (예: "a@x.com,b@y.com")
     */
    @PostMapping("/health/alert")
    public ResponseEntity<Map<String, Object>> healthAlert(@RequestBody Map<String, String> request) {
        String to = request.get("to");
        String subject = request.get("subject");
        String body = request.get("body");

        if (to == null || subject == null || body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "to, subject, body 필수"));
        }

        List<String> toEmails = Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (toEmails.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "to 에 유효한 수신자가 없습니다"));
        }

        String html = body.replace("\n", "<br>");
        int sent = 0;
        int failed = 0;
        for (String toEmail : toEmails) {
            try {
                // OPERATIONAL — email_log 기록
                emailService.send(EmailCategory.OPERATIONAL, toEmail, "health_alert", subject, html);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("헬스 체크 알림 발송 실패 to={} 사유={}", toEmail, e.getMessage());
            }
        }
        log.info("헬스 체크 알림 발송 — 성공 {}건 / 실패 {}건 / 대상 {}", sent, failed, toEmails);
        if (sent == 0) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "failed",
                    "sent", sent,
                    "failed", failed
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "sent", sent,
                "failed", failed
        ));
    }

    @GetMapping("/health/email")
    public ResponseEntity<Map<String, Object>> emailHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("email", emailService.getDiagnostics());
        return ResponseEntity.ok(response);
    }
}
