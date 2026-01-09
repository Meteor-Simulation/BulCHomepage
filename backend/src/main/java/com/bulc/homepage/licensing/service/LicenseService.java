package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.dto.ValidationResponse.GlobalSessionInfo;
import com.bulc.homepage.licensing.dto.ValidationResponse.LicenseCandidate;
import com.bulc.homepage.licensing.dto.ValidationResponse.TerminatedSessionInfo;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
@Slf4j
@Service
@Transactional(readOnly = true)
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final ActivationRepository activationRepository;
    private final LicensePlanRepository planRepository;
    private final ProductRepository productRepository;
    private final SessionTokenService sessionTokenService;
    private final OfflineTokenService offlineTokenService;

    // v0.3.0: Auto-Resolve용 stale 판정 기준 (분)
    private final int staleThresholdMinutes;

    public LicenseService(LicenseRepository licenseRepository,
                          ActivationRepository activationRepository,
                          LicensePlanRepository planRepository,
                          ProductRepository productRepository,
                          SessionTokenService sessionTokenService,
                          OfflineTokenService offlineTokenService,
                          @Value("${bulc.licensing.stale-threshold-minutes:30}") int staleThresholdMinutes) {
        this.licenseRepository = licenseRepository;
        this.activationRepository = activationRepository;
        this.planRepository = planRepository;
        this.productRepository = productRepository;
        this.sessionTokenService = sessionTokenService;
        this.offlineTokenService = offlineTokenService;
        this.staleThresholdMinutes = staleThresholdMinutes;
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
            // v0.3.0: 동시 세션 수 확인 - ALL_LICENSES_FULL 응답
            int sessionTtlMinutes = license.getSessionTtlMinutes();
            Instant sessionThreshold = now.minusSeconds(sessionTtlMinutes * 60L);
            long activeCount = activationRepository.countActiveSessions(license.getId(), sessionThreshold);

            if (activeCount >= license.getMaxConcurrentSessions()) {
                Instant staleThreshold = now.minusSeconds(staleThresholdMinutes * 60L);
                List<GlobalSessionInfo> sessions = buildGlobalSessionInfoList(
                        List.of(license), sessionThreshold, staleThreshold);
                return ValidationResponse.allLicensesFull(sessions);
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

        // v1.1.3: 오프라인 토큰 발급 (갱신 임계값 정책 적용)
        if (shouldRenewOfflineToken(activation, license)) {
            OfflineTokenService.OfflineToken offlineToken = generateOfflineToken(license, activation);
            if (offlineToken != null) {
                activation.issueOfflineToken(offlineToken.token(), offlineToken.expiresAt());
            }
        }

        licenseRepository.save(license);

        // Entitlements 추출
        List<String> entitlements = extractEntitlements(license);

        // v1.1.2: sessionToken 생성 (RS256 전용, null 가능 - dev에서 키 미설정 시)
        String productCode = resolveProductCode(license.getProductId());
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                license.getId(), productCode, request.deviceFingerprint(), entitlements);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                sessionToken != null ? sessionToken.token() : null,
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
     * v0.3.0: 계정 기반 라이선스 검증 및 활성화 (Auto-Resolve).
     *
     * Two-Pass Algorithm:
     * 1. FIRST PASS - 빈 슬롯 찾기 (stale 종료 없이):
     *    - Device Affinity: 현재 기기가 이미 활성 세션이 있는 라이선스 우선
     *    - 빈 슬롯이 있는 라이선스 선택
     *
     * 2. SECOND PASS - Stale 자동 종료:
     *    - staleThreshold (30분) 기준으로 stale 세션 탐색
     *    - 가장 오래된 stale 세션 1개만 종료
     *    - AUTO_RECOVERED 응답
     *
     * 3. ALL_LICENSES_FULL (409):
     *    - 모든 후보 라이선스의 세션을 GlobalSessionInfo로 통합
     *    - USER_ACTION_REQUIRED 응답
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

            return performValidationWithAutoResolve(license, request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs(), request.deviceDisplayName());
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

        // v0.3.0: Two-Pass Algorithm으로 Auto-Resolve
        return autoResolveValidation(candidates, request.deviceFingerprint(),
                request.clientVersion(), request.clientOs(), request.deviceDisplayName());
    }

    /**
     * v0.3.0: Two-Pass Algorithm으로 라이선스 자동 선택 및 활성화.
     */
    private ValidationResponse autoResolveValidation(List<License> candidates, String deviceFingerprint,
                                                      String clientVersion, String clientOs, String deviceDisplayName) {
        Instant now = Instant.now();
        int sessionTtlMinutes = candidates.get(0).getSessionTtlMinutes(); // 모든 라이선스가 동일하다고 가정
        Instant sessionThreshold = now.minusSeconds(sessionTtlMinutes * 60L);
        Instant staleThreshold = now.minusSeconds(staleThresholdMinutes * 60L);

        // 후보 라이선스 정렬: ACTIVE > EXPIRED_GRACE > latest validUntil
        List<License> sortedCandidates = sortCandidatesByPriority(candidates, now);

        // ========================================
        // FIRST PASS: 빈 슬롯 찾기 (stale 종료 없이)
        // ========================================

        // Pass 1-a: Device Affinity - 현재 기기가 이미 활성 세션이 있는 라이선스 우선
        for (License license : sortedCandidates) {
            Optional<Activation> existingSession = license.getActivations().stream()
                    .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint)
                            && a.getStatus() == ActivationStatus.ACTIVE
                            && !a.getLastSeenAt().isBefore(sessionThreshold))
                    .findFirst();

            if (existingSession.isPresent()) {
                log.debug("Auto-Resolve: Device affinity - 기존 세션 발견, licenseId={}", license.getId());
                return performValidationWithAutoResolve(license, deviceFingerprint,
                        clientVersion, clientOs, deviceDisplayName);
            }
        }

        // Pass 1-b: 빈 슬롯이 있는 라이선스 찾기
        for (License license : sortedCandidates) {
            long activeSessionCount = activationRepository.countActiveSessions(license.getId(), sessionThreshold);
            int maxConcurrentSessions = license.getMaxConcurrentSessions();

            if (activeSessionCount < maxConcurrentSessions) {
                log.debug("Auto-Resolve: 빈 슬롯 발견, licenseId={}, active={}/{}",
                        license.getId(), activeSessionCount, maxConcurrentSessions);
                return performValidationWithAutoResolve(license, deviceFingerprint,
                        clientVersion, clientOs, deviceDisplayName);
            }
        }

        // ========================================
        // SECOND PASS: Stale 세션 자동 종료
        // ========================================

        for (License license : sortedCandidates) {
            List<Activation> staleSessions = activationRepository.findStaleSessions(license.getId(), staleThreshold);

            if (!staleSessions.isEmpty()) {
                // 가장 오래된 stale 세션 1개만 종료
                Activation staleSession = staleSessions.get(0);
                String terminatedDeviceName = staleSession.getDeviceDisplayName();
                Instant terminatedLastSeen = staleSession.getLastSeenAt();

                log.info("Auto-Resolve: Stale 세션 자동 종료, activationId={}, licenseId={}, lastSeenAt={}",
                        staleSession.getId(), license.getId(), staleSession.getLastSeenAt());

                staleSession.deactivate("AUTO_RESOLVE_STALE");
                activationRepository.save(staleSession);

                // 새 세션 활성화
                ValidationResponse response = performValidationWithAutoResolve(license, deviceFingerprint,
                        clientVersion, clientOs, deviceDisplayName);

                // AUTO_RECOVERED 응답으로 변환
                if (response.valid()) {
                    TerminatedSessionInfo terminatedSession = new TerminatedSessionInfo(
                            terminatedDeviceName != null ? terminatedDeviceName : "Unknown Device",
                            terminatedLastSeen
                    );
                    return ValidationResponse.successWithRecovery(
                            response.licenseId(),
                            response.status(),
                            response.validUntil(),
                            response.entitlements(),
                            response.sessionToken(),
                            response.offlineToken(),
                            response.offlineTokenExpiresAt(),
                            terminatedSession
                    );
                }
                return response;
            }
        }

        // ========================================
        // ALL_LICENSES_FULL: 모든 라이선스 full
        // ========================================

        log.info("Auto-Resolve: 모든 라이선스 full, userId={}, candidates={}",
                candidates.get(0).getOwnerId(), candidates.size());

        // 모든 후보 라이선스의 세션을 GlobalSessionInfo로 통합
        List<GlobalSessionInfo> allSessions = buildGlobalSessionInfoList(sortedCandidates, sessionThreshold, staleThreshold);

        return ValidationResponse.allLicensesFull(allSessions);
    }

    /**
     * v0.3.0: 후보 라이선스를 우선순위로 정렬.
     * 정렬 기준: ACTIVE > EXPIRED_GRACE > latest validUntil
     */
    private List<License> sortCandidatesByPriority(List<License> candidates, Instant now) {
        return candidates.stream()
                .sorted((l1, l2) -> {
                    LicenseStatus s1 = l1.calculateEffectiveStatus(now);
                    LicenseStatus s2 = l2.calculateEffectiveStatus(now);

                    // ACTIVE 우선
                    if (s1 == LicenseStatus.ACTIVE && s2 != LicenseStatus.ACTIVE) return -1;
                    if (s1 != LicenseStatus.ACTIVE && s2 == LicenseStatus.ACTIVE) return 1;

                    // EXPIRED_GRACE 다음
                    if (s1 == LicenseStatus.EXPIRED_GRACE && s2 != LicenseStatus.EXPIRED_GRACE) return -1;
                    if (s1 != LicenseStatus.EXPIRED_GRACE && s2 == LicenseStatus.EXPIRED_GRACE) return 1;

                    // 같은 상태면 validUntil 최신순
                    Instant v1 = l1.getValidUntil() != null ? l1.getValidUntil() : Instant.MAX;
                    Instant v2 = l2.getValidUntil() != null ? l2.getValidUntil() : Instant.MAX;
                    return v2.compareTo(v1);
                })
                .collect(Collectors.toList());
    }

    /**
     * v0.3.0: 모든 후보 라이선스의 세션을 GlobalSessionInfo로 통합.
     */
    private List<GlobalSessionInfo> buildGlobalSessionInfoList(List<License> candidates,
                                                                 Instant sessionThreshold,
                                                                 Instant staleThreshold) {
        List<GlobalSessionInfo> allSessions = new ArrayList<>();

        for (License license : candidates) {
            // 해당 라이선스의 활성 세션 조회 (TTL 기반)
            List<Activation> activeSessions = activationRepository.findActiveSessions(
                    license.getId(), sessionThreshold);

            // 플랜명 조회
            String planName = "기본 플랜";
            if (license.getPlanId() != null) {
                planName = planRepository.findById(license.getPlanId())
                        .map(LicensePlan::getName)
                        .orElse("알 수 없는 플랜");
            }

            // 제품명 조회
            String productName = resolveProductCode(license.getProductId());

            final String finalPlanName = planName;
            final String finalProductName = productName;

            List<GlobalSessionInfo> licenseSessions = activeSessions.stream()
                    .map(a -> new GlobalSessionInfo(
                            license.getId(),
                            finalProductName,
                            finalPlanName,
                            a.getId(),
                            a.getDeviceDisplayName(),
                            maskFingerprint(a.getDeviceFingerprint()),
                            a.getLastSeenAt(),
                            a.getClientOs(),
                            a.getClientVersion(),
                            a.getLastSeenAt().isBefore(staleThreshold)  // isStale 판정
                    ))
                    .toList();

            allSessions.addAll(licenseSessions);
        }

        // 최신 접속순으로 정렬
        return allSessions.stream()
                .sorted(Comparator.comparing(GlobalSessionInfo::lastSeenAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * v0.3.0: Auto-Resolve 검증 로직.
     * 상태 검증 후 활성화 수행.
     */
    private ValidationResponse performValidationWithAutoResolve(License license, String deviceFingerprint,
                                                                  String clientVersion, String clientOs,
                                                                  String deviceDisplayName) {
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

        // 총 기기 활성화 수 확인 (ACTIVE + STALE 상태)
        if (!license.canActivate(deviceFingerprint, now)) {
            return ValidationResponse.failure(
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.name(),
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.getMessage()
            );
        }

        // 활성화 추가/갱신
        Activation activation = license.addActivation(deviceFingerprint, clientVersion,
                                                       clientOs, null, deviceDisplayName);

        // 오프라인 토큰 발급 (갱신 임계값 정책 적용)
        if (shouldRenewOfflineToken(activation, license)) {
            OfflineTokenService.OfflineToken offlineToken = generateOfflineToken(license, activation);
            if (offlineToken != null) {
                activation.issueOfflineToken(offlineToken.token(), offlineToken.expiresAt());
            }
        }

        licenseRepository.save(license);

        List<String> entitlements = extractEntitlements(license);

        // sessionToken 생성 (RS256 전용, null 가능 - dev에서 키 미설정 시)
        String productCode = resolveProductCode(license.getProductId());
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                license.getId(), productCode, deviceFingerprint, entitlements);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                sessionToken != null ? sessionToken.token() : null,
                activation.getOfflineToken(),
                activation.getOfflineTokenExpiresAt()
        );
    }

    /**
     * v0.3.0: 계정 기반 Heartbeat.
     * Bearer token 인증된 사용자의 활성화 상태를 갱신합니다.
     *
     * Heartbeat은 validate와 달리:
     * - 이미 활성화된 기기에서만 호출 가능
     * - 새로운 기기 활성화는 불가
     * - Auto-Resolve로 라이선스 자동 선택
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
            return performHeartbeat(license, request.deviceFingerprint(),
                    request.clientVersion(), request.clientOs());
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

        // v0.3.0: Auto-Resolve - Device Affinity로 라이선스 선택
        Instant now = Instant.now();
        List<License> sortedCandidates = sortCandidatesByPriority(candidates, now);

        // 현재 기기가 활성화된 라이선스 찾기
        for (License license : sortedCandidates) {
            // 먼저 해당 기기의 활성화가 존재하는지 확인 (상태 무관)
            Optional<Activation> anyActivation = license.getActivations().stream()
                    .filter(a -> a.getDeviceFingerprint().equals(request.deviceFingerprint()))
                    .findFirst();

            if (anyActivation.isPresent()) {
                Activation activation = anyActivation.get();
                // 비활성화된 세션인 경우 SESSION_DEACTIVATED 반환
                if (activation.getStatus() == ActivationStatus.DEACTIVATED) {
                    throw new LicenseException(ErrorCode.SESSION_DEACTIVATED);
                }
                // 활성 세션인 경우 heartbeat 수행
                if (activation.getStatus() == ActivationStatus.ACTIVE) {
                    return performHeartbeat(license, request.deviceFingerprint(),
                            request.clientVersion(), request.clientOs());
                }
            }
        }

        // 기존 활성화 없음 - heartbeat 불가
        throw new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND);
    }

    /**
     * v0.3.0: Heartbeat 전용 검증 로직.
     * 기존 활성화만 갱신, 새 활성화 생성 안함.
     */
    private ValidationResponse performHeartbeat(License license, String deviceFingerprint,
                                                  String clientVersion, String clientOs) {
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

        // 기존 활성화 찾기
        Optional<Activation> existingActivation = license.getActivations().stream()
                .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint))
                .findFirst();

        if (existingActivation.isEmpty()) {
            throw new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND);
        }

        Activation activation = existingActivation.get();

        // 비활성화되었거나 만료된 상태 (다른 기기에서 force로 비활성화됨)
        if (activation.getStatus() == ActivationStatus.DEACTIVATED ||
            activation.getStatus() == ActivationStatus.EXPIRED) {
            throw new LicenseException(ErrorCode.SESSION_DEACTIVATED);
        }

        // 세션 갱신
        activation.updateHeartbeat(clientVersion, clientOs, null);

        // 오프라인 토큰 갱신 (임계값 정책 적용)
        if (shouldRenewOfflineToken(activation, license)) {
            OfflineTokenService.OfflineToken offlineToken = generateOfflineToken(license, activation);
            if (offlineToken != null) {
                activation.issueOfflineToken(offlineToken.token(), offlineToken.expiresAt());
            }
        }

        licenseRepository.save(license);

        List<String> entitlements = extractEntitlements(license);

        // sessionToken 생성
        String productCode = resolveProductCode(license.getProductId());
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                license.getId(), productCode, deviceFingerprint, entitlements);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                sessionToken != null ? sessionToken.token() : null,
                activation.getOfflineToken(),
                activation.getOfflineTokenExpiresAt()
        );
    }

    // ==========================================
    // v1.1.1 동시 세션 관리 메서드
    // ==========================================

    /**
     * v0.3.0: 강제 검증 및 활성화 (Session Kick).
     *
     * ALL_LICENSES_FULL (409) 응답 후 클라이언트가 선택한 세션을 비활성화하고 새 세션을 활성화.
     * /validate에서 409 ALL_LICENSES_FULL 응답을 받은 후 호출.
     *
     * 처리 순서:
     * 1. 라이선스 락 획득 (FOR UPDATE)
     * 2. 비활성화 대상 세션이 해당 라이선스 소유인지 검증
     * 3. 대상 세션들 비활성화 (FORCE_VALIDATE 사유)
     * 4. 새 세션 활성화
     * 5. 동시 세션 수 재검증 (race condition 방지)
     *
     * @param userId 인증된 사용자 ID
     * @param request 강제 검증 요청 (licenseId, deactivateActivationIds 포함)
     */
    @Transactional
    public ValidationResponse forceValidateByUser(UUID userId, ForceValidateRequest request) {
        // 비관적 락으로 라이선스 조회
        License license = licenseRepository.findByIdWithLock(request.licenseId())
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

        // 소유자 검증
        if (!license.isOwnedBy(userId)) {
            throw new LicenseException(ErrorCode.ACCESS_DENIED);
        }

        Instant now = Instant.now();

        // 비활성화 대상 세션 검증
        List<Activation> toDeactivate = activationRepository.findByIdIn(request.deactivateActivationIds());

        // 모든 대상 세션이 해당 라이선스 소유인지 확인
        for (Activation activation : toDeactivate) {
            if (!activation.getLicense().getId().equals(license.getId())) {
                throw new LicenseException(ErrorCode.INVALID_ACTIVATION_OWNERSHIP,
                        "비활성화 대상 세션 " + activation.getId() + "은(는) 해당 라이선스에 속하지 않습니다");
            }
        }

        // 대상 세션들 비활성화
        for (Activation activation : toDeactivate) {
            if (activation.getStatus() == ActivationStatus.ACTIVE) {
                activation.deactivate("FORCE_VALIDATE");
            }
        }
        activationRepository.saveAll(toDeactivate);

        // 세션 TTL 기반 활성 세션 재계산
        int sessionTtlMinutes = license.getSessionTtlMinutes();
        Instant sessionThreshold = now.minusSeconds(sessionTtlMinutes * 60L);
        Instant staleThreshold = now.minusSeconds(staleThresholdMinutes * 60L);

        // 동시 세션 수 재검증
        long remainingActiveCount = activationRepository.countActiveSessions(license.getId(), sessionThreshold);

        // 본인이 이미 활성 세션이 있는 경우 제외
        boolean hasSelfActiveSession = license.getActivations().stream()
                .anyMatch(a -> a.getDeviceFingerprint().equals(request.deviceFingerprint())
                        && a.getStatus() == ActivationStatus.ACTIVE
                        && !a.getLastSeenAt().isBefore(sessionThreshold));

        if (!hasSelfActiveSession && remainingActiveCount >= license.getMaxConcurrentSessions()) {
            // Race condition 발생: 다른 기기가 먼저 활성화됨
            // v0.3.0: GlobalSessionInfo 형식으로 응답
            List<GlobalSessionInfo> sessionInfoList = buildGlobalSessionInfoList(
                    List.of(license), sessionThreshold, staleThreshold);

            return ValidationResponse.allLicensesFull(sessionInfoList);
        }

        // 새 세션 활성화
        Activation newActivation = license.addActivation(
                request.deviceFingerprint(),
                request.clientVersion(),
                request.clientOs(),
                null,
                request.deviceDisplayName()
        );

        // 오프라인 토큰 발급 (갱신 임계값 정책 적용)
        if (shouldRenewOfflineToken(newActivation, license)) {
            OfflineTokenService.OfflineToken offlineToken = generateOfflineToken(license, newActivation);
            if (offlineToken != null) {
                newActivation.issueOfflineToken(offlineToken.token(), offlineToken.expiresAt());
            }
        }

        licenseRepository.save(license);

        LicenseStatus effectiveStatus = license.calculateEffectiveStatus(now);
        List<String> entitlements = extractEntitlements(license);

        // sessionToken 생성 (RS256 전용, null 가능 - dev에서 키 미설정 시)
        String productCode = resolveProductCode(license.getProductId());
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                license.getId(), productCode, request.deviceFingerprint(), entitlements);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                sessionToken != null ? sessionToken.token() : null,
                newActivation.getOfflineToken(),
                newActivation.getOfflineTokenExpiresAt()
        );
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
        return performValidation(license, deviceFingerprint, clientVersion, clientOs, clientIp,
                                 allowNewActivation, null);
    }

    /**
     * 검증 로직 공통 메서드 (v1.1.1 deviceDisplayName 지원).
     */
    private ValidationResponse performValidation(License license, String deviceFingerprint,
                                                  String clientVersion, String clientOs, String clientIp,
                                                  boolean allowNewActivation, String deviceDisplayName) {
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

        // v1.1.1: 세션 TTL 기반 활성 세션 계산
        int sessionTtlMinutes = license.getSessionTtlMinutes();
        Instant sessionThreshold = now.minusSeconds(sessionTtlMinutes * 60L);

        // 기존 활성화 확인 (TTL 내에서 active인 것만)
        Optional<Activation> existingActivation = license.getActivations().stream()
                .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint)
                        && a.getStatus() == ActivationStatus.ACTIVE
                        && !a.getLastSeenAt().isBefore(sessionThreshold))
                .findFirst();

        // v1.1.1: Heartbeat 모드에서 기존 활성화가 비활성화되었는지 확인
        if (!allowNewActivation) {
            Optional<Activation> anyExisting = license.getActivations().stream()
                    .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint))
                    .findFirst();

            if (anyExisting.isEmpty()) {
                throw new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND);
            }

            Activation activation = anyExisting.get();
            // 비활성화되었거나 만료된 상태 (다른 기기에서 force로 비활성화됨)
            if (activation.getStatus() == ActivationStatus.DEACTIVATED ||
                activation.getStatus() == ActivationStatus.EXPIRED) {
                throw new LicenseException(ErrorCode.SESSION_DEACTIVATED);
            }

            // TTL이 지났는데 ACTIVE인 경우 (클라이언트가 오래 중단되었다가 복귀)
            // 이 경우는 heartbeat을 허용하여 세션을 갱신
        }

        // v1.1.1: 동시 세션 수 확인 (TTL 기반)
        List<Activation> activeSessions = activationRepository.findActiveSessions(
                license.getId(), sessionThreshold);

        // 본인 세션은 제외하고 카운트 (재접속 시)
        long otherActiveSessionCount = activeSessions.stream()
                .filter(a -> !a.getDeviceFingerprint().equals(deviceFingerprint))
                .count();

        int maxConcurrentSessions = license.getMaxConcurrentSessions();

        // v0.3.0: 새 활성화 요청인데 이미 동시 세션 제한에 도달한 경우
        if (existingActivation.isEmpty() && otherActiveSessionCount >= maxConcurrentSessions) {
            // 409 ALL_LICENSES_FULL + GlobalSessionInfo 목록 반환
            Instant staleThreshold = now.minusSeconds(staleThresholdMinutes * 60L);
            List<GlobalSessionInfo> sessionInfoList = buildGlobalSessionInfoList(
                    List.of(license), sessionThreshold, staleThreshold);

            return ValidationResponse.allLicensesFull(sessionInfoList);
        }

        // 총 기기 활성화 수 확인 (ACTIVE + STALE 상태)
        if (!license.canActivate(deviceFingerprint, now)) {
            return ValidationResponse.failure(
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.name(),
                    ErrorCode.ACTIVATION_LIMIT_EXCEEDED.getMessage()
            );
        }

        // 활성화 추가/갱신 (deviceDisplayName 포함)
        Activation activation = license.addActivation(deviceFingerprint, clientVersion,
                                                       clientOs, clientIp, deviceDisplayName);

        // v1.1.3: 오프라인 토큰 발급 (갱신 임계값 정책 적용)
        if (shouldRenewOfflineToken(activation, license)) {
            OfflineTokenService.OfflineToken offlineToken = generateOfflineToken(license, activation);
            if (offlineToken != null) {
                activation.issueOfflineToken(offlineToken.token(), offlineToken.expiresAt());
            }
        }

        licenseRepository.save(license);

        List<String> entitlements = extractEntitlements(license);

        // v1.1.2: sessionToken 생성 (RS256 전용, null 가능 - dev에서 키 미설정 시)
        String productCode = resolveProductCode(license.getProductId());
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                license.getId(), productCode, deviceFingerprint, entitlements);

        return ValidationResponse.success(
                license.getId(),
                effectiveStatus,
                license.getValidUntil(),
                entitlements,
                sessionToken != null ? sessionToken.token() : null,
                activation.getOfflineToken(),
                activation.getOfflineTokenExpiresAt()
        );
    }

    /**
     * v1.1.1: 기기 fingerprint 마스킹 (보안).
     */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() <= 8) {
            return "****";
        }
        return fingerprint.substring(0, 4) + "****" + fingerprint.substring(fingerprint.length() - 4);
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
            // product code를 기반으로 deterministic UUID 생성
            return UUID.nameUUIDFromBytes(product.getCode().getBytes());
        }
        // 둘 다 없으면 null (모든 제품 대상 검색)
        return null;
    }

    /**
     * v1.1.2: productId를 productCode로 변환.
     * sessionToken의 aud 클레임에 사용.
     */
    private String resolveProductCode(UUID productId) {
        if (productId == null) {
            return "UNKNOWN";
        }
        return productRepository.findAll().stream()
                .filter(p -> UUID.nameUUIDFromBytes(p.getCode().getBytes()).equals(productId))
                .findFirst()
                .map(Product::getCode)
                .orElse("PRODUCT_" + productId.toString().substring(0, 8));
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
                "sessionTtlMinutes", 60,  // v1.1.1: 세션 TTL (분)
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
     * v1.1.3: RS256 서명된 오프라인 토큰 생성.
     *
     * OfflineTokenService에 위임하여 RS256 서명된 토큰 생성.
     * Claims (v1.1.3 통일): iss, aud, sub, typ, dfp, ent, iat, exp
     * absolute cap: exp ≤ licenseValidUntil
     */
    private OfflineTokenService.OfflineToken generateOfflineToken(License license, Activation activation) {
        String productCode = resolveProductCode(license.getProductId());
        List<String> entitlements = extractEntitlements(license);
        int offlineDays = getOfflineTokenValidDays(license);

        return offlineTokenService.generateOfflineToken(
                license.getId(),
                productCode,
                activation.getDeviceFingerprint(),
                entitlements,
                offlineDays,
                license.getValidUntil()  // absolute cap
        );
    }

    /**
     * v1.1.3: 갱신 임계값에 따라 offlineToken 갱신 필요 여부 확인.
     */
    private boolean shouldRenewOfflineToken(Activation activation, License license) {
        int offlineDays = getOfflineTokenValidDays(license);
        return offlineTokenService.shouldRenew(activation.getOfflineTokenExpiresAt(), offlineDays);
    }

    /**
     * v1.1.3: AUTO_PICK_BEST 전략 - 최적의 라이선스 선택.
     * 우선순위: ACTIVE > EXPIRED_GRACE > 최신 validUntil
     */
    private License selectBestLicense(List<License> candidates) {
        Instant now = Instant.now();

        // ACTIVE 상태 우선
        Optional<License> active = candidates.stream()
                .filter(l -> l.calculateEffectiveStatus(now) == LicenseStatus.ACTIVE)
                .max(Comparator.comparing(l -> l.getValidUntil() != null ? l.getValidUntil() : Instant.MAX));

        if (active.isPresent()) {
            return active.get();
        }

        // EXPIRED_GRACE 상태
        Optional<License> grace = candidates.stream()
                .filter(l -> l.calculateEffectiveStatus(now) == LicenseStatus.EXPIRED_GRACE)
                .max(Comparator.comparing(l -> l.getValidUntil() != null ? l.getValidUntil() : Instant.MAX));

        if (grace.isPresent()) {
            return grace.get();
        }

        // 그 외: 가장 최근 validUntil
        return selectLatestLicense(candidates);
    }

    /**
     * v1.1.3: AUTO_PICK_LATEST 전략 - 가장 최근 validUntil인 라이선스 선택.
     */
    private License selectLatestLicense(List<License> candidates) {
        return candidates.stream()
                .max(Comparator.comparing(l -> l.getValidUntil() != null ? l.getValidUntil() : Instant.MAX))
                .orElse(candidates.get(0));
    }
}
