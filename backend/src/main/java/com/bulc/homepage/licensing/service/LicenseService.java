package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.dto.ValidationResponse.LicenseCandidate;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 라이선스 서비스.
 *
 * 이 서비스는 두 가지 용도로 사용됩니다:
 *
 * 1. 내부 모듈용 (Billing, Admin 등에서 직접 호출)
 *    - issueLicense(): 결제 완료 시 Billing에서 호출
 *    - revokeLicense(), revokeLicenseByOrderId(): 환불 시 Billing에서 호출
 *    - suspendLicense(): 관리자가 Admin 모듈에서 호출
 *    - renewLicense(): 구독 갱신 시 Billing에서 호출
 *
 * 2. 클라이언트 API용 (Controller에서 호출)
 *    - validateAndActivate(): 클라이언트 앱의 라이선스 검증/활성화
 *    - getLicense(), getLicenseByKey(): 라이선스 정보 조회
 *    - getLicensesByOwner(): 사용자의 라이선스 목록 조회
 *    - deactivate(): 사용자가 직접 기기 해제
 *
 * 주의: 발급/정지/회수/갱신은 HTTP API로 노출하지 않습니다.
 * 이러한 작업은 반드시 Billing 또는 Admin 모듈을 통해 트리거되어야 합니다.
 */
@Service
@Transactional(readOnly = true)
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final ActivationRepository activationRepository;
    private final LicensePlanRepository planRepository;
    private final ProductRepository productRepository;
    private final SecretKey offlineTokenKey;

    public LicenseService(LicenseRepository licenseRepository,
                          ActivationRepository activationRepository,
                          LicensePlanRepository planRepository,
                          ProductRepository productRepository,
                          @org.springframework.beans.factory.annotation.Value("${jwt.secret}") String jwtSecret) {
        this.licenseRepository = licenseRepository;
        this.activationRepository = activationRepository;
        this.planRepository = planRepository;
        this.productRepository = productRepository;
        // JWT secret을 offline token 서명에도 사용 (별도 secret 추가 가능)
        this.offlineTokenKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ==========================================
    // 내부 모듈용 메서드 (Billing, Admin에서 호출)
    // HTTP API로 노출하지 않음
    // ==========================================

    /**
     * 라이선스 발급.
     *
     * Billing 모듈에서 결제 완료(OrderPaid) 시 호출합니다.
     * HTTP API로 노출하지 않습니다.
     *
     * 사용 예시 (BillingService):
     * <pre>
     * {@code
     * @Transactional
     * public void completePayment(PaymentResult result) {
     *     Order order = orderRepository.findById(result.orderId());
     *     order.markPaid(result.paidAt());
     *
     *     licensingService.issueLicense(new LicenseIssueRequest(
     *         OwnerType.USER,
     *         order.getUserId(),
     *         order.getProductId(),
     *         order.getPlanId(),
     *         LicenseType.SUBSCRIPTION,
     *         null, null,
     *         order.getValidUntil(),
     *         policySnapshot,
     *         order.getId()
     *     ));
     * }
     * }
     * </pre>
     */
    @Transactional
    public LicenseResponse issueLicense(LicenseIssueRequest request) {
        // 동일 제품에 대한 기존 라이선스 확인
        licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                request.ownerType(), request.ownerId(), request.productId()
        ).ifPresent(existing -> {
            if (existing.getStatus() != LicenseStatus.REVOKED) {
                throw new LicenseException(ErrorCode.LICENSE_ALREADY_EXISTS);
            }
        });

        // 라이선스 키 생성
        String licenseKey = generateLicenseKey();

        License license = License.builder()
                .ownerType(request.ownerType())
                .ownerId(request.ownerId())
                .productId(request.productId())
                .planId(request.planId())
                .licenseType(request.licenseType())
                .usageCategory(request.usageCategoryOrDefault())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .policySnapshot(request.policySnapshot() != null ? request.policySnapshot() : defaultPolicySnapshot())
                .licenseKey(licenseKey)
                .sourceOrderId(request.sourceOrderId())
                .build();

        // 발급 즉시 활성화 (결제 완료된 경우)
        license.activate();

        License saved = licenseRepository.save(license);
        return LicenseResponse.from(saved);
    }

    /**
     * Plan 기반 라이선스 발급.
     *
     * Billing 모듈에서 결제 완료(OrderPaid) 시 planId와 함께 호출합니다.
     * Plan에서 PolicySnapshot을 자동으로 생성하여 License에 저장합니다.
     *
     * 사용 예시 (BillingService):
     * <pre>
     * {@code
     * @Transactional
     * public void completePayment(PaymentResult result) {
     *     Order order = orderRepository.findById(result.orderId());
     *     order.markPaid(result.paidAt());
     *
     *     licensingService.issueLicenseWithPlan(
     *         OwnerType.USER,
     *         order.getUserId(),
     *         order.getPlanId(),
     *         order.getId(),
     *         UsageCategory.COMMERCIAL
     *     );
     * }
     * }
     * </pre>
     */
    @Transactional
    public LicenseResponse issueLicenseWithPlan(OwnerType ownerType, UUID ownerId,
                                                UUID planId, UUID sourceOrderId,
                                                UsageCategory usageCategory) {
        // Plan 조회 (활성화되고 삭제되지 않은 플랜만)
        LicensePlan plan = planRepository.findAvailableById(planId)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_AVAILABLE));

        // 동일 제품에 대한 기존 라이선스 확인
        licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                ownerType, ownerId, plan.getProductId()
        ).ifPresent(existing -> {
            if (existing.getStatus() != LicenseStatus.REVOKED) {
                throw new LicenseException(ErrorCode.LICENSE_ALREADY_EXISTS);
            }
        });

        // 라이선스 키 생성
        String licenseKey = generateLicenseKey();

        // Plan에서 PolicySnapshot 생성
        Map<String, Object> policySnapshot = plan.toPolicySnapshot();

        // 유효기간 계산
        Instant now = Instant.now();
        Instant validUntil = plan.getLicenseType() == LicenseType.PERPETUAL
                ? null
                : now.plusSeconds((long) plan.getDurationDays() * 24 * 60 * 60);

        License license = License.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .productId(plan.getProductId())
                .planId(planId)
                .licenseType(plan.getLicenseType())
                .usageCategory(usageCategory != null ? usageCategory : UsageCategory.COMMERCIAL)
                .validFrom(now)
                .validUntil(validUntil)
                .policySnapshot(policySnapshot)
                .licenseKey(licenseKey)
                .sourceOrderId(sourceOrderId)
                .build();

        // 발급 즉시 활성화 (결제 완료된 경우)
        license.activate();

        License saved = licenseRepository.save(license);
        return LicenseResponse.from(saved);
    }

    /**
     * Plan 코드 기반 라이선스 발급.
     * planId 대신 planCode로 조회하여 발급합니다.
     */
    @Transactional
    public LicenseResponse issueLicenseWithPlanCode(OwnerType ownerType, UUID ownerId,
                                                    String planCode, UUID sourceOrderId,
                                                    UsageCategory usageCategory) {
        // Plan 조회 (코드로)
        LicensePlan plan = planRepository.findAvailableByCode(planCode)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_AVAILABLE));

        return issueLicenseWithPlan(ownerType, ownerId, plan.getId(), sourceOrderId, usageCategory);
    }

    // ==========================================
    // 클라이언트 API용 메서드 (Controller에서 호출)
    // ==========================================

    /**
     * 라이선스 조회 (ID).
     * 클라이언트/마이페이지에서 라이선스 상세 정보 조회 시 사용.
     */
    public LicenseResponse getLicense(UUID licenseId) {
        License license = findLicenseOrThrow(licenseId);
        return LicenseResponse.from(license);
    }

    /**
     * 라이선스 조회 (라이선스 키).
     * 클라이언트가 라이선스 키로 정보 조회 시 사용.
     */
    public LicenseResponse getLicenseByKey(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));
        return LicenseResponse.from(license);
    }

    /**
     * 사용자의 라이선스 목록 조회.
     * 마이페이지에서 사용자의 라이선스 목록 조회 시 사용.
     * TODO: 인증된 사용자만 자신의 라이선스를 조회할 수 있도록 권한 체크 필요
     */
    public List<LicenseResponse> getLicensesByOwner(OwnerType ownerType, UUID ownerId) {
        return licenseRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .stream()
                .map(LicenseResponse::from)
                .toList();
    }

    /**
     * 라이선스 검증 및 활성화.
     * 클라이언트 앱 실행 시 호출하여 라이선스 유효성 확인 및 기기 활성화.
     * 동시성 제어를 위해 비관적 락 사용.
     */
    @Transactional
    public ValidationResponse validateAndActivate(String licenseKey, ActivationRequest request) {
        // 비관적 락으로 라이선스 조회 (race condition 방지)
        License license = licenseRepository.findByLicenseKeyWithLock(licenseKey)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

        Instant now = Instant.now();
        LicenseStatus effectiveStatus = license.calculateEffectiveStatus(now);

        // 상태 검증
        switch (effectiveStatus) {
            case EXPIRED_HARD -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_EXPIRED.name(),
                        ErrorCode.LICENSE_EXPIRED.getMessage()
                );
            }
            case SUSPENDED -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_SUSPENDED.name(),
                        ErrorCode.LICENSE_SUSPENDED.getMessage()
                );
            }
            case REVOKED -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_REVOKED.name(),
                        ErrorCode.LICENSE_REVOKED.getMessage()
                );
            }
            case PENDING -> {
                return ValidationResponse.failure(
                        ErrorCode.INVALID_LICENSE_STATE.name(),
                        "라이선스가 아직 활성화되지 않았습니다"
                );
            }
            // ACTIVE, EXPIRED_GRACE는 계속 진행
        }

        // 활성화 가능 여부 확인
        if (!license.canActivate(request.deviceFingerprint(), now)) {
            // 동시 세션 수 확인
            long activeCount = activationRepository.countByLicenseIdAndStatus(
                    license.getId(), ActivationStatus.ACTIVE
            );
            if (activeCount >= license.getMaxConcurrentSessions()) {
                return ValidationResponse.failure(
                        ErrorCode.CONCURRENT_SESSION_LIMIT_EXCEEDED.name(),
                        ErrorCode.CONCURRENT_SESSION_LIMIT_EXCEEDED.getMessage()
                );
            }
            return ValidationResponse.failure(
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.name(),
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.getMessage()
            );
        }

        // 활성화 추가/갱신
        Activation activation = license.addActivation(
                request.deviceFingerprint(),
                request.clientVersion(),
                request.clientOs(),
                request.clientIp()
        );

        // 오프라인 토큰 발급 (필요시)
        if (!activation.hasValidOfflineToken(now)) {
            int offlineDays = getOfflineTokenValidDays(license);
            String offlineToken = generateOfflineToken(license, activation);
            activation.issueOfflineToken(offlineToken, now.plusSeconds(offlineDays * 24L * 60 * 60));
        }

        licenseRepository.save(license);

        // Entitlements 추출
        List<String> entitlements = extractEntitlements(license);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                activation.getOfflineToken(),
                activation.getOfflineTokenExpiresAt()
        );
    }

    /**
     * 기기 비활성화.
     * 사용자가 특정 기기에서 라이선스를 해제할 때 사용.
     */
    @Transactional
    public void deactivate(UUID licenseId, String deviceFingerprint) {
        License license = findLicenseOrThrow(licenseId);

        Activation activation = activationRepository
                .findByLicenseIdAndDeviceFingerprint(licenseId, deviceFingerprint)
                .orElseThrow(() -> new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND));

        activation.deactivate();
        activationRepository.save(activation);
    }

    // ==========================================
    // v1.1 계정 기반 API 메서드
    // ==========================================

    /**
     * 내 라이선스 목록 조회.
     * 현재 로그인한 사용자의 라이선스 목록을 조회합니다.
     *
     * v1.1에서 추가됨.
     */
    public List<MyLicenseView> getMyLicenses(UUID userId, UUID productId, LicenseStatus status) {
        List<License> licenses = licenseRepository.findByUserIdWithFilters(userId, productId, status);
        return licenses.stream()
                .map(MyLicenseView::from)
                .collect(Collectors.toList());
    }

    /**
     * 계정 기반 라이선스 검증 및 활성화.
     * Bearer token 인증된 사용자의 라이선스를 검증합니다.
     *
     * v1.1에서 추가됨.
     *
     * 복수 라이선스 선택 로직:
     * - licenseId 지정: 해당 라이선스 사용 (소유자 검증)
     * - licenseId 미지정:
     *   - 후보 0개: LICENSE_NOT_FOUND_FOR_PRODUCT (404)
     *   - 후보 1개: 자동 선택
     *   - 후보 2개 이상: LICENSE_SELECTION_REQUIRED (409) + candidates 반환
     *
     * @param userId 인증된 사용자 ID
     * @param request 검증 요청 (productId/productCode, deviceFingerprint 포함)
     */
    @Transactional
    public ValidationResponse validateAndActivateByUser(UUID userId, ValidateRequest request) {
        // productId 확인 (productCode → productId 변환 지원)
        UUID productId = resolveProductId(request);

        List<LicenseStatus> validStatuses = List.of(LicenseStatus.ACTIVE, LicenseStatus.EXPIRED_GRACE);

        // licenseId가 지정된 경우: 해당 라이선스 직접 사용
        if (request.licenseId() != null) {
            License license = licenseRepository.findByIdWithLock(request.licenseId())
                    .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

            // 소유자 검증
            if (!license.isOwnedBy(userId)) {
                throw new LicenseException(ErrorCode.ACCESS_DENIED);
            }

            // 제품 검증 (지정된 productId와 일치해야 함)
            if (productId != null && !license.getProductId().equals(productId)) {
                throw new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT,
                        "지정된 라이선스가 해당 제품에 속하지 않습니다");
            }

            return performValidation(license, request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs(), null, true);
        }

        // licenseId 미지정: 후보 검색
        List<License> candidates;
        if (productId != null) {
            candidates = licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    OwnerType.USER, userId, productId, validStatuses);
        } else {
            // productId도 없으면 사용자의 모든 유효 라이선스 조회
            candidates = licenseRepository.findByOwnerAndStatusInWithLock(
                    OwnerType.USER, userId, validStatuses);
        }

        // 후보 0개: LICENSE_NOT_FOUND_FOR_PRODUCT (404)
        if (candidates.isEmpty()) {
            throw new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT);
        }

        // 후보 1개: 자동 선택
        if (candidates.size() == 1) {
            return performValidation(candidates.get(0), request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs(), null, true);
        }

        // 후보 2개 이상: LICENSE_SELECTION_REQUIRED (409) + candidates 반환
        List<LicenseCandidate> candidateList = buildCandidateList(candidates);
        return ValidationResponse.selectionRequired(candidateList);
    }

    /**
     * 계정 기반 Heartbeat.
     * Bearer token 인증된 사용자의 활성화 상태를 갱신합니다.
     *
     * v1.1에서 추가됨.
     *
     * Heartbeat은 validate와 달리:
     * - 이미 활성화된 기기에서만 호출 가능
     * - 새로운 기기 활성화는 불가
     *
     * @param userId 인증된 사용자 ID
     * @param request 검증 요청 (productId/productCode, licenseId, deviceFingerprint 포함)
     */
    @Transactional
    public ValidationResponse heartbeatByUser(UUID userId, ValidateRequest request) {
        // productId 확인 (productCode → productId 변환 지원)
        UUID productId = resolveProductId(request);

        List<LicenseStatus> validStatuses = List.of(LicenseStatus.ACTIVE, LicenseStatus.EXPIRED_GRACE);

        // licenseId가 지정된 경우: 해당 라이선스 직접 사용
        if (request.licenseId() != null) {
            License license = licenseRepository.findByIdWithLock(request.licenseId())
                    .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

            // 소유자 검증
            if (!license.isOwnedBy(userId)) {
                throw new LicenseException(ErrorCode.ACCESS_DENIED);
            }

            // Heartbeat은 기존 활성화만 갱신 (새 활성화 생성 안함)
            return performValidation(license, request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs(), null, false);
        }

        // licenseId 미지정: 후보 검색
        List<License> candidates;
        if (productId != null) {
            candidates = licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    OwnerType.USER, userId, productId, validStatuses);
        } else {
            candidates = licenseRepository.findByOwnerAndStatusInWithLock(
                    OwnerType.USER, userId, validStatuses);
        }

        // 후보 0개: LICENSE_NOT_FOUND_FOR_PRODUCT (404)
        if (candidates.isEmpty()) {
            throw new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT);
        }

        // 후보 1개: 자동 선택
        if (candidates.size() == 1) {
            return performValidation(candidates.get(0), request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs(), null, false);
        }

        // 후보 2개 이상: LICENSE_SELECTION_REQUIRED (409)
        List<LicenseCandidate> candidateList = buildCandidateList(candidates);
        return ValidationResponse.selectionRequired(candidateList);
    }

    /**
     * 기기 비활성화 (소유자 검증 포함).
     * v1.1에서 추가됨 - 본인 소유 라이선스만 비활성화 가능.
     */
    @Transactional
    public void deactivateWithOwnerCheck(UUID userId, UUID licenseId, String deviceFingerprint) {
        License license = findLicenseOrThrow(licenseId);

        // 소유자 검증
        if (!license.isOwnedBy(userId)) {
            throw new LicenseException(ErrorCode.ACCESS_DENIED);
        }

        Activation activation = activationRepository
                .findByLicenseIdAndDeviceFingerprint(licenseId, deviceFingerprint)
                .orElseThrow(() -> new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND));

        activation.deactivate();
        activationRepository.save(activation);
    }

    /**
     * 라이선스 상세 조회 (소유자 검증 포함).
     * v1.1에서 추가됨 - 본인 소유 라이선스만 조회 가능.
     */
    public LicenseResponse getLicenseWithOwnerCheck(UUID userId, UUID licenseId) {
        License license = findLicenseOrThrow(licenseId);

        // 소유자 검증
        if (!license.isOwnedBy(userId)) {
            throw new LicenseException(ErrorCode.ACCESS_DENIED);
        }

        return LicenseResponse.from(license);
    }

    /**
     * 검증 로직 공통 메서드.
     */
    private ValidationResponse performValidation(License license, String deviceFingerprint,
                                                  String clientVersion, String clientOs, String clientIp,
                                                  boolean allowNewActivation) {
        Instant now = Instant.now();
        LicenseStatus effectiveStatus = license.calculateEffectiveStatus(now);

        // 상태 검증
        switch (effectiveStatus) {
            case EXPIRED_HARD -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_EXPIRED.name(),
                        ErrorCode.LICENSE_EXPIRED.getMessage()
                );
            }
            case SUSPENDED -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_SUSPENDED.name(),
                        ErrorCode.LICENSE_SUSPENDED.getMessage()
                );
            }
            case REVOKED -> {
                return ValidationResponse.failure(
                        ErrorCode.LICENSE_REVOKED.name(),
                        ErrorCode.LICENSE_REVOKED.getMessage()
                );
            }
            case PENDING -> {
                return ValidationResponse.failure(
                        ErrorCode.INVALID_LICENSE_STATE.name(),
                        "라이선스가 아직 활성화되지 않았습니다"
                );
            }
            // ACTIVE, EXPIRED_GRACE는 계속 진행
        }

        // 기존 활성화 확인
        boolean hasExistingActivation = license.getActivations().stream()
                .anyMatch(a -> a.getDeviceFingerprint().equals(deviceFingerprint)
                        && a.getStatus() == ActivationStatus.ACTIVE);

        // Heartbeat 모드에서 기존 활성화 없으면 에러 (404 반환)
        if (!allowNewActivation && !hasExistingActivation) {
            throw new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND);
        }

        // 활성화 가능 여부 확인
        if (!license.canActivate(deviceFingerprint, now)) {
            long activeCount = activationRepository.countByLicenseIdAndStatus(
                    license.getId(), ActivationStatus.ACTIVE
            );
            if (activeCount >= license.getMaxConcurrentSessions()) {
                return ValidationResponse.failure(
                        ErrorCode.CONCURRENT_SESSION_LIMIT_EXCEEDED.name(),
                        ErrorCode.CONCURRENT_SESSION_LIMIT_EXCEEDED.getMessage()
                );
            }
            return ValidationResponse.failure(
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.name(),
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.getMessage()
            );
        }

        // 활성화 추가/갱신
        Activation activation = license.addActivation(deviceFingerprint, clientVersion, clientOs, clientIp);

        // 오프라인 토큰 발급 (필요시)
        if (!activation.hasValidOfflineToken(now)) {
            int offlineDays = getOfflineTokenValidDays(license);
            String offlineToken = generateOfflineToken(license, activation);
            activation.issueOfflineToken(offlineToken, now.plusSeconds(offlineDays * 24L * 60 * 60));
        }

        licenseRepository.save(license);

        List<String> entitlements = extractEntitlements(license);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                activation.getOfflineToken(),
                activation.getOfflineTokenExpiresAt()
        );
    }

    // ==========================================
    // 내부 모듈용 메서드 (Billing, Admin에서 호출)
    // HTTP API로 노출하지 않음
    // ==========================================

    /**
     * 라이선스 정지.
     *
     * Admin 모듈에서 관리자가 라이선스를 정지할 때 호출합니다.
     * HTTP API로 노출하지 않습니다.
     */
    @Transactional
    public LicenseResponse suspendLicense(UUID licenseId, String reason) {
        License license = findLicenseOrThrow(licenseId);
        license.suspend(reason);
        return LicenseResponse.from(licenseRepository.save(license));
    }

    /**
     * 라이선스 회수 (ID로 조회).
     *
     * Admin 모듈에서 관리자가 라이선스를 회수할 때 호출합니다.
     * HTTP API로 노출하지 않습니다.
     */
    @Transactional
    public LicenseResponse revokeLicense(UUID licenseId, String reason) {
        License license = findLicenseOrThrow(licenseId);
        license.revoke(reason);
        return LicenseResponse.from(licenseRepository.save(license));
    }

    /**
     * 주문 ID로 라이선스 회수.
     *
     * Billing 모듈에서 환불(OrderRefunded) 시 호출합니다.
     * HTTP API로 노출하지 않습니다.
     *
     * 사용 예시 (BillingService):
     * <pre>
     * {@code
     * @Transactional
     * public void processRefund(RefundResult result) {
     *     Order order = orderRepository.findById(result.orderId());
     *     order.markRefunded();
     *
     *     licensingService.revokeLicenseByOrderId(order.getId(), "REFUNDED");
     * }
     * }
     * </pre>
     */
    @Transactional
    public LicenseResponse revokeLicenseByOrderId(UUID orderId, String reason) {
        License license = licenseRepository.findBySourceOrderId(orderId)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));
        license.revoke(reason);
        return LicenseResponse.from(licenseRepository.save(license));
    }

    /**
     * 구독 갱신.
     *
     * Billing 모듈에서 구독 갱신 결제 완료 시 호출합니다.
     * HTTP API로 노출하지 않습니다.
     */
    @Transactional
    public LicenseResponse renewLicense(UUID licenseId, Instant newValidUntil) {
        License license = findLicenseOrThrow(licenseId);
        license.renew(newValidUntil);
        return LicenseResponse.from(licenseRepository.save(license));
    }

    // === Private 헬퍼 메서드 ===

    private License findLicenseOrThrow(UUID licenseId) {
        return licenseRepository.findById(licenseId)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));
    }

    /**
     * productCode 또는 productId를 UUID로 변환.
     * productCode가 있으면 Product 조회 후 Long id를 UUID로 변환.
     */
    private UUID resolveProductId(ValidateRequest request) {
        if (request.productId() != null) {
            return request.productId();
        }
        if (request.productCode() != null) {
            Product product = productRepository.findByCodeAndIsActiveTrue(request.productCode())
                    .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT,
                            "제품을 찾을 수 없습니다: " + request.productCode()));
            // Long id를 UUID로 변환 (least significant bits에 저장)
            return new UUID(0, product.getId());
        }
        // 둘 다 없으면 null (모든 제품 대상 검색)
        return null;
    }

    /**
     * 라이선스 목록을 LicenseCandidate 목록으로 변환.
     */
    private List<LicenseCandidate> buildCandidateList(List<License> licenses) {
        Instant now = Instant.now();
        return licenses.stream()
                .map(license -> {
                    // planId로 플랜명 조회 (없으면 기본값)
                    String planName = "기본 플랜";
                    if (license.getPlanId() != null) {
                        planName = planRepository.findById(license.getPlanId())
                                .map(LicensePlan::getName)
                                .orElse("알 수 없는 플랜");
                    }

                    // 활성 기기 수 계산
                    int activeDevices = (int) license.getActivations().stream()
                            .filter(a -> a.getStatus() == ActivationStatus.ACTIVE)
                            .count();

                    // 소유자 범위 표시
                    String ownerScope = license.getOwnerType() == OwnerType.USER ? "개인" : "조직";

                    return new LicenseCandidate(
                            license.getId(),
                            planName,
                            license.getLicenseType().name(),
                            license.calculateEffectiveStatus(now),
                            license.getValidUntil(),
                            ownerScope,
                            activeDevices,
                            license.getMaxActivations(),
                            null  // 사용자 지정 라벨 (현재 미지원)
                    );
                })
                .collect(Collectors.toList());
    }

    private String generateLicenseKey() {
        // 형식: XXXX-XXXX-XXXX-XXXX
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return String.format("%s-%s-%s-%s",
                uuid.substring(0, 4),
                uuid.substring(4, 8),
                uuid.substring(8, 12),
                uuid.substring(12, 16)
        );
    }

    private Map<String, Object> defaultPolicySnapshot() {
        return Map.of(
                "maxActivations", 3,
                "maxConcurrentSessions", 2,
                "gracePeriodDays", 7,
                "allowOfflineDays", 30,
                "entitlements", List.of("core-simulation")
        );
    }

    private int getOfflineTokenValidDays(License license) {
        if (license.getPolicySnapshot() != null &&
                license.getPolicySnapshot().containsKey("allowOfflineDays")) {
            return ((Number) license.getPolicySnapshot().get("allowOfflineDays")).intValue();
        }
        return 30; // 기본값
    }

    @SuppressWarnings("unchecked")
    private List<String> extractEntitlements(License license) {
        if (license.getPolicySnapshot() != null &&
                license.getPolicySnapshot().containsKey("entitlements")) {
            Object entitlements = license.getPolicySnapshot().get("entitlements");
            if (entitlements instanceof List<?>) {
                return (List<String>) entitlements;
            }
        }
        return List.of("core-simulation");
    }

    /**
     * JWS 서명된 오프라인 토큰 생성.
     *
     * 토큰 페이로드:
     * - sub: licenseId
     * - deviceFingerprint: 기기 고유 식별자
     * - validUntil: 라이선스 만료일
     * - maxActivations: 최대 기기 수
     * - entitlements: 권한 목록
     * - iat: 발급 시각
     * - exp: 오프라인 토큰 만료 시각
     *
     * 클라이언트는 이 토큰을 로컬에서 검증하여 오프라인 실행 가능.
     * 서명 검증 실패 시 온라인 재검증 필요.
     */
    private String generateOfflineToken(License license, Activation activation) {
        Instant now = Instant.now();
        int offlineDays = getOfflineTokenValidDays(license);
        Instant expiration = now.plusSeconds(offlineDays * 24L * 60 * 60);

        return Jwts.builder()
                .subject(license.getId().toString())
                .claim("deviceFingerprint", activation.getDeviceFingerprint())
                .claim("validUntil", license.getValidUntil() != null
                        ? license.getValidUntil().toEpochMilli() : null)
                .claim("maxActivations", license.getMaxActivations())
                .claim("entitlements", extractEntitlements(license))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(offlineTokenKey)
                .compact();
    }
}
