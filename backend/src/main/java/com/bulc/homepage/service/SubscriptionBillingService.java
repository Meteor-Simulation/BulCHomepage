package com.bulc.homepage.service;

import com.bulc.homepage.entity.BillingKey;
import com.bulc.homepage.entity.Subscription;
import com.bulc.homepage.entity.SubscriptionPayment;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.BillingKeyRepository;
import com.bulc.homepage.repository.SubscriptionPaymentRepository;
import com.bulc.homepage.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionBillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final BillingKeyService billingKeyService;
    private final LicenseService licenseService;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 자동 갱신 활성화
     */
    @Transactional
    public Subscription enableAutoRenew(Long subscriptionId, Long billingKeyId, String billingCycle, UUID userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다."));

        if (!subscription.getUserId().equals(userId)) {
            throw new RuntimeException("구독 접근 권한이 없습니다.");
        }

        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 빌링키입니다."));

        if (!billingKey.getUserId().equals(userId)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        subscription.enableAutoRenew(billingKeyId, billingCycle);
        subscriptionRepository.save(subscription);

        log.info("자동 갱신 활성화: subscriptionId={}, billingKeyId={}, cycle={}",
                subscriptionId, billingKeyId, billingCycle);
        return subscription;
    }

    /**
     * 자동 갱신 비활성화
     */
    @Transactional
    public Subscription disableAutoRenew(Long subscriptionId, UUID userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다."));

        if (!subscription.getUserId().equals(userId)) {
            throw new RuntimeException("구독 접근 권한이 없습니다.");
        }

        subscription.disableAutoRenew();
        subscriptionRepository.save(subscription);

        log.info("자동 갱신 비활성화: subscriptionId={}", subscriptionId);
        return subscription;
    }

    /**
     * 갱신 대상 구독 결제 처리 (스케줄러에서 호출)
     */
    @Transactional
    public void processDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> dueSubscriptions = subscriptionRepository.findDueForRenewal(now);

        log.info("갱신 대상 구독 수: {}", dueSubscriptions.size());

        for (Subscription subscription : dueSubscriptions) {
            try {
                processSubscriptionRenewal(subscription);
            } catch (Exception e) {
                log.error("구독 갱신 처리 실패: subscriptionId={}, error={}",
                        subscription.getId(), e.getMessage());
            }
        }
    }

    /**
     * 단일 구독 갱신 처리
     */
    @Transactional
    public void processSubscriptionRenewal(Subscription subscription) {
        log.info("구독 갱신 처리 시작: subscriptionId={}", subscription.getId());

        if (subscription.getBillingKeyId() == null) {
            log.warn("빌링키가 설정되지 않은 구독: subscriptionId={}", subscription.getId());
            subscription.disableAutoRenew();
            subscriptionRepository.save(subscription);
            return;
        }

        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(subscription.getBillingKeyId())
                .orElse(null);

        if (billingKey == null) {
            log.warn("유효하지 않은 빌링키: subscriptionId={}, billingKeyId={}",
                    subscription.getId(), subscription.getBillingKeyId());
            subscription.disableAutoRenew();
            subscriptionRepository.save(subscription);
            return;
        }

        // 결제 금액 계산
        BigDecimal amount = subscription.getPricePlan().getPrice();

        // 주문 ID 생성
        String orderId = "SUB-" + subscription.getId() + "-" + System.currentTimeMillis();

        // 결제 이력 생성
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .subscriptionId(subscription.getId())
                .billingKeyId(billingKey.getId())
                .orderId(orderId)
                .amount(amount)
                .billingDate(LocalDate.now())
                .status(SubscriptionPayment.PaymentStatus.PENDING)
                .build();
        subscriptionPaymentRepository.save(payment);

        // 결제 요청
        try {
            String orderName = subscription.getProduct().getName() + " 구독 갱신";
            Map<String, Object> result = billingKeyService.requestBillingPayment(
                    billingKey.getId(),
                    orderId,
                    orderName,
                    amount.intValue(),
                    subscription.getUserId()
            );

            if (Boolean.TRUE.equals(result.get("success"))) {
                // 결제 성공
                payment.markAsSuccess((String) result.get("paymentKey"));
                subscriptionPaymentRepository.save(payment);

                // 구독 갱신
                subscription.renew();
                subscriptionRepository.save(subscription);

                // 라이선스 갱신
                renewLicense(subscription);

                log.info("구독 갱신 성공: subscriptionId={}, orderId={}", subscription.getId(), orderId);
            } else {
                payment.markAsFailed("결제 API 응답 실패");
                subscriptionPaymentRepository.save(payment);
                log.error("구독 갱신 결제 실패: subscriptionId={}", subscription.getId());
            }
        } catch (Exception e) {
            payment.markAsFailed(e.getMessage());
            subscriptionPaymentRepository.save(payment);
            log.error("구독 갱신 결제 오류: subscriptionId={}, error={}", subscription.getId(), e.getMessage());
        }
    }

    /**
     * 실패한 결제 재시도 (스케줄러에서 호출)
     */
    @Transactional
    public void retryFailedPayments() {
        List<SubscriptionPayment> failedPayments = subscriptionPaymentRepository.findRetryablePayments();

        log.info("재시도 대상 결제 수: {}", failedPayments.size());

        for (SubscriptionPayment payment : failedPayments) {
            try {
                retryPayment(payment);
            } catch (Exception e) {
                log.error("결제 재시도 실패: paymentId={}, error={}", payment.getId(), e.getMessage());
            }
        }
    }

    /**
     * 단일 결제 재시도
     */
    @Transactional
    public void retryPayment(SubscriptionPayment payment) {
        if (payment.getRetryCount() >= MAX_RETRY_COUNT) {
            log.warn("최대 재시도 횟수 초과: paymentId={}", payment.getId());
            return;
        }

        Subscription subscription = subscriptionRepository.findById(payment.getSubscriptionId())
                .orElse(null);

        if (subscription == null || !subscription.getAutoRenew()) {
            log.warn("재시도 대상 구독이 없거나 자동갱신 비활성화: paymentId={}", payment.getId());
            payment.markAsCanceled();
            subscriptionPaymentRepository.save(payment);
            return;
        }

        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(payment.getBillingKeyId())
                .orElse(null);

        if (billingKey == null) {
            log.warn("유효하지 않은 빌링키로 재시도 불가: paymentId={}", payment.getId());
            payment.markAsCanceled();
            subscriptionPaymentRepository.save(payment);
            return;
        }

        // 새로운 주문 ID 생성
        String newOrderId = "SUB-" + subscription.getId() + "-RETRY-" + payment.getRetryCount() + "-" + System.currentTimeMillis();
        payment.setOrderId(newOrderId);
        payment.setStatus(SubscriptionPayment.PaymentStatus.PENDING);
        subscriptionPaymentRepository.save(payment);

        try {
            String orderName = subscription.getProduct().getName() + " 구독 갱신 (재시도)";
            Map<String, Object> result = billingKeyService.requestBillingPayment(
                    billingKey.getId(),
                    newOrderId,
                    orderName,
                    payment.getAmount().intValue(),
                    subscription.getUserId()
            );

            if (Boolean.TRUE.equals(result.get("success"))) {
                payment.markAsSuccess((String) result.get("paymentKey"));
                subscriptionPaymentRepository.save(payment);

                subscription.renew();
                subscriptionRepository.save(subscription);

                renewLicense(subscription);

                log.info("결제 재시도 성공: paymentId={}, orderId={}", payment.getId(), newOrderId);
            } else {
                payment.markAsFailed("재시도 결제 API 응답 실패");
                subscriptionPaymentRepository.save(payment);
            }
        } catch (Exception e) {
            payment.markAsFailed("재시도 오류: " + e.getMessage());
            subscriptionPaymentRepository.save(payment);
            log.error("결제 재시도 오류: paymentId={}, error={}", payment.getId(), e.getMessage());
        }
    }

    /**
     * 만료된 구독 처리 (스케줄러에서 호출)
     */
    @Transactional
    public void processExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expiredSubscriptions = subscriptionRepository.findExpiredWithoutAutoRenew(now);

        log.info("만료 대상 구독 수: {}", expiredSubscriptions.size());

        for (Subscription subscription : expiredSubscriptions) {
            subscription.expire();
            subscriptionRepository.save(subscription);
            log.info("구독 만료 처리: subscriptionId={}", subscription.getId());
        }
    }

    /**
     * 라이선스 갱신
     * TODO: 라이선스 시스템 연결 시 아래 주석 해제
     */
    private void renewLicense(Subscription subscription) {
        if (subscription.getPricePlan() == null) {
            log.warn("요금제가 설정되지 않은 구독: subscriptionId={}", subscription.getId());
            return;
        }

        // TODO: 라이선스 시스템 연결 시 아래 로직 활성화
        /*
        try {
            UUID userId = UUID.nameUUIDFromBytes(subscription.getUserEmail().getBytes(StandardCharsets.UTF_8));
            UUID sourceOrderId = UUID.nameUUIDFromBytes(("subscription-" + subscription.getId() + "-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));

            licenseService.issueLicenseWithPlanForBilling(
                    OwnerType.USER,
                    userId,
                    subscription.getPricePlan().getLicensePlanId(),
                    sourceOrderId,
                    UsageCategory.COMMERCIAL
            );

            log.info("라이선스 갱신 성공: subscriptionId={}", subscription.getId());
        } catch (Exception e) {
            log.error("라이선스 갱신 실패: subscriptionId={}, error={}", subscription.getId(), e.getMessage());
        }
        */

        log.info("구독 갱신 처리 완료 (라이선스 발급 대기): subscriptionId={}", subscription.getId());
    }

    /**
     * 구독 결제 이력 조회
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPayment> getPaymentHistory(Long subscriptionId, UUID userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다."));

        if (!subscription.getUserId().equals(userId)) {
            throw new RuntimeException("구독 접근 권한이 없습니다.");
        }

        return subscriptionPaymentRepository.findBySubscriptionIdOrderByBillingDateDesc(subscriptionId);
    }
}
