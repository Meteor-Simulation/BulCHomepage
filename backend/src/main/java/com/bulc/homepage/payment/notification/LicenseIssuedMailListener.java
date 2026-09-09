package com.bulc.homepage.payment.notification;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.OperationalMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 라이선스 발급 완료 메일을 발송한다 (MDP-833).
 *
 * <p><b>AFTER_COMMIT 인 이유</b> — 발급은 결제 트랜잭션
 * ({@code confirmPayment} · {@code payWithBillingKey} · {@code handlePaymentComplete}) 안에서 일어난다.
 * 동기 발송하면 이후 트랜잭션이 롤백됐을 때 존재하지 않는 라이선스의 키를 메일로 보내게 된다.
 * 커밋이 확정된 뒤에만 발송한다.
 *
 * <p><b>{@code fallbackExecution = true} 인 이유</b> — 트랜잭션 밖에서 발급하는 호출부가
 * 나중에 생기더라도 통지가 조용히 사라지지 않게 한다. 현재 호출부는 모두 트랜잭션 안이다.
 *
 * <p><b>{@code @Async} 인 이유</b> — 메일 발송(MS Graph API)이 결제 응답 스레드를 잡지 않도록 한다.
 *
 * <p><b>예외를 삼키는 이유</b> — 라이선스는 발급 즉시 ACTIVE 이고 메일은 통지 수단일 뿐
 * 활성화 게이트가 아니다. 발송 실패가 제품 사용에 영향을 주면 안 된다.
 * 실패는 {@code email_log} 에 FAILED 로 남는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseIssuedMailListener {

    private final UserRepository userRepository;
    private final OperationalMailService operationalMailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLicenseIssued(LicenseIssuedEvent event) {
        try {
            Optional<User> userOpt = userRepository.findById(event.userId());
            if (userOpt.isEmpty()) {
                log.warn("[발급통지] 사용자 없음 - userId={}, sourceOrderId={}",
                        event.userId(), event.sourceOrderId());
                return;
            }

            String email = userOpt.get().getEmail();
            if (email == null || email.isBlank()) {
                log.warn("[발급통지] 이메일 없음 - userId={}, sourceOrderId={}",
                        event.userId(), event.sourceOrderId());
                return;
            }

            operationalMailService.sendLicenseIssuedNotice(
                    email.trim(), event.licenseKey(), event.validUntil(), event.recovered());

            log.info("[발급통지] 발송 완료 - userId={}, licenseId={}, recovered={}",
                    event.userId(), event.licenseId(), event.recovered());
        } catch (Exception e) {
            log.error("[발급통지] 발송 실패 - userId={}, sourceOrderId={}, error={}",
                    event.userId(), event.sourceOrderId(), e.getMessage(), e);
        }
    }
}
