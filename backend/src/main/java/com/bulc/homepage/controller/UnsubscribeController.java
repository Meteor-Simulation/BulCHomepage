package com.bulc.homepage.controller;

import com.bulc.homepage.service.UnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 광고성 메일 수신거부 API (MDP-607). 인증 불필요 — 메일 링크에서 토큰으로 접근.
 */
@RestController
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeService unsubscribeService;

    @PostMapping("/api/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        UnsubscribeService.Result result = unsubscribeService.unsubscribe(token);

        if (result == UnsubscribeService.Result.NOT_FOUND) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "유효하지 않거나 만료된 수신거부 링크입니다."
            ));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
