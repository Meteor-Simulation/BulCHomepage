package com.bulc.homepage.controller;

import com.bulc.homepage.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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
    @GetMapping("/health/email")
    public ResponseEntity<Map<String, Object>> emailHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("email", emailService.getDiagnostics());
        return ResponseEntity.ok(response);
    }
}
