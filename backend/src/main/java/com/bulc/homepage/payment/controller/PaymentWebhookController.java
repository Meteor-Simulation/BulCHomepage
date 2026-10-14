package com.bulc.homepage.payment.controller;

import com.bulc.homepage.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 토스페이먼츠 웹훅 수신부.
 *
 * <p>해석·검증은 모두 {@link PaymentWebhookService} 가 한다. 여기서는 결과를 HTTP 상태로
 * 옮기는 일만 한다 — 토스는 2xx 가 아니면 최대 7회 재시도하므로 이 매핑이 재시도 여부를 정한다.
 *
 * <p>종전에는 {@code Authorization} 헤더를 검증해 없으면 403 을 돌려줬는데, 토스 웹훅은 그
 * 헤더를 보내지 않아 정상 요청이 전부 거부됐다. 자세한 경위는 서비스 클래스 주석에 적어 뒀다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload) {
        log.info("[웹훅] 수신: {}", payload);

        PaymentWebhookService.Result result = paymentWebhookService.handle(payload);

        return switch (result) {
            // 처리했거나, 처리할 것이 없다. 어느 쪽이든 토스가 다시 보낼 필요는 없다.
            case PROCESSED, IGNORED -> ResponseEntity.ok().build();
            // secret 이 맞지 않는 요청. 우리가 만든 결제가 아니라고 알린다.
            case REJECTED -> ResponseEntity.status(403).build();
            // 일시적 실패. 2xx 가 아니어야 토스가 다시 보낸다.
            case RETRY -> ResponseEntity.status(503).build();
        };
    }

    /** 배포 후 엔드포인트가 열려 있는지 확인하는 용도 */
    @GetMapping("/webhook/test")
    public ResponseEntity<String> testWebhook() {
        return ResponseEntity.ok("Webhook endpoint is working");
    }
}
