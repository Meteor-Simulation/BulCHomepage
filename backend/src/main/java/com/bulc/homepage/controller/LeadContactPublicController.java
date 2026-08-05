package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.LeadContactPublicRequest;
import com.bulc.homepage.service.LeadContactService;
import com.bulc.homepage.service.PublicFormRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MDP-707: 전시회·세미나 현장 공개 메일링 등록 API.
 *
 * <p>QR 로 접속한 방문자가 로그인 없이 직접 제출한다. 인증이 없으므로
 * honeypot + IP 레이트리밋으로 자동 제출을 1차 차단한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/lead-contacts/public")
@RequiredArgsConstructor
public class LeadContactPublicController {

    private final LeadContactService leadContactService;
    private final PublicFormRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody LeadContactPublicRequest req,
                                    HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);

        // honeypot: 사람에게 보이지 않는 입력란이 채워졌다면 봇으로 간주하고 조용히 성공 응답
        if (req.getWebsite() != null && !req.getWebsite().isBlank()) {
            log.warn("공개 폼 honeypot 감지 - ip={}", clientIp);
            return ResponseEntity.ok(Map.of("success", true));
        }

        if (!rateLimiter.tryAcquire(clientIp)) {
            log.warn("공개 폼 레이트리밋 초과 - ip={}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "message", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
            ));
        }

        try {
            leadContactService.registerPublic(req, clientIp, httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "등록이 완료되었습니다."
            ));
        } catch (Exception e) {
            log.error("공개 폼 등록 실패 - ip={}", clientIp, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "등록에 실패했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
    }

    /**
     * 클라이언트 IP 추출. 현재 운영은 nginx 리버스 프록시 뒤에 있으므로
     * X-Forwarded-For 의 첫 번째 값을 우선 사용한다.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
