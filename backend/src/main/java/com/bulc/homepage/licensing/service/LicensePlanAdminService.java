package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.LicensePlan;
import com.bulc.homepage.licensing.dto.LicensePlanRequest;
import com.bulc.homepage.licensing.dto.LicensePlanResponse;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 라이선스 플랜 Admin 서비스.
 * Admin UI에서 플랜을 관리하기 위한 CRUD 기능 제공.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LicensePlanAdminService {

    private final LicensePlanRepository planRepository;
    private final ProductRepository productRepository;
    private final EntitlementRegistry entitlementRegistry;

    /**
     * 플랜 목록 조회.
     *
     * @param pageable   페이지네이션
     * @param activeOnly 활성화된 플랜만 조회
     * @param productId  특정 제품의 플랜만 조회 (null이면 전체)
     */
    @Transactional(readOnly = true)
    public Page<LicensePlanResponse> listPlans(Pageable pageable, Boolean activeOnly, UUID productId) {
        Page<LicensePlan> page;

        if (productId != null) {
            if (Boolean.TRUE.equals(activeOnly)) {
                page = planRepository.findAllByDeletedFalseAndActiveTrueAndProductId(productId, pageable);
            } else {
                page = planRepository.findAllByDeletedFalseAndProductId(productId, pageable);
            }
        } else if (Boolean.TRUE.equals(activeOnly)) {
            page = planRepository.findAllByDeletedFalseAndActiveTrue(pageable);
        } else {
            page = planRepository.findAllByDeletedFalse(pageable);
        }

        return page.map(LicensePlanResponse::fromEntity);
    }

    /**
     * 플랜 상세 조회.
     */
    @Transactional(readOnly = true)
    public LicensePlanResponse getPlan(UUID id) {
        LicensePlan plan = planRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        return LicensePlanResponse.fromEntity(plan);
    }

    /**
     * 새 플랜 생성.
     */
    public LicensePlanResponse createPlan(LicensePlanRequest request) {
        // 코드 중복 체크
        if (planRepository.existsByCodeAndDeletedFalse(request.code())) {
            throw new LicenseException(ErrorCode.PLAN_CODE_DUPLICATE,
                    "이미 존재하는 플랜 코드입니다: " + request.code());
        }

        // MDP-789: entitlement 어휘 레지스트리 검증 (제품 스코프)
        validateEntitlements(request.productId(), request.entitlements());

        LicensePlan plan = LicensePlan.builder()
                .productId(request.productId())
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .licenseType(request.licenseType())
                .durationDays(request.durationDays())
                .graceDays(request.graceDays())
                .maxActivations(request.maxActivations())
                .maxConcurrentSessions(request.maxConcurrentSessions())
                .allowOfflineDays(request.allowOfflineDays())
                .build();

        plan.setEntitlements(request.entitlements() != null ? request.entitlements() : List.of());

        LicensePlan saved = planRepository.save(plan);
        return LicensePlanResponse.fromEntity(saved);
    }

    /**
     * 플랜 수정.
     */
    public LicensePlanResponse updatePlan(UUID id, LicensePlanRequest request) {
        LicensePlan plan = planRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        // 코드 변경 시 중복 체크
        if (!plan.getCode().equals(request.code()) &&
            planRepository.existsByCodeAndDeletedFalse(request.code())) {
            throw new LicenseException(ErrorCode.PLAN_CODE_DUPLICATE,
                    "이미 존재하는 플랜 코드입니다: " + request.code());
        }

        // MDP-789: entitlement 어휘 레지스트리 검증 (제품 스코프).
        // 제품은 플랜 수정으로 바뀌지 않으므로 기존 plan 의 productId 기준.
        validateEntitlements(plan.getProductId(), request.entitlements());

        plan.update(
                request.code(),
                request.name(),
                request.description(),
                request.licenseType(),
                request.durationDays(),
                request.graceDays(),
                request.maxActivations(),
                request.maxConcurrentSessions(),
                request.allowOfflineDays()
        );

        plan.setEntitlements(request.entitlements() != null ? request.entitlements() : List.of());

        return LicensePlanResponse.fromEntity(plan);
    }

    /**
     * MDP-789: 플랜의 entitlement 키가 제품 스코프의 정본 어휘인지 검증.
     *
     * 레지스트리가 미구성(설정 없음)이면 검증을 생략한다 (개발 편의).
     * 운영은 반드시 {@code bulc.licensing.entitlements.by-product} 를 설정한다.
     *
     * @param productId    플랜이 속한 제품 ID
     * @param entitlements 검증할 entitlement 키 목록 (null 허용)
     * @throws LicenseException INVALID_ENTITLEMENT_KEY - 등록되지 않은 키가 있을 때
     */
    private void validateEntitlements(UUID productId, List<String> entitlements) {
        if (entitlementRegistry.isEmpty() || entitlements == null || entitlements.isEmpty()) {
            return;
        }

        String productCode = resolveProductCode(productId);
        List<String> invalid = entitlements.stream()
                .filter(key -> !entitlementRegistry.isAllowed(productCode, key))
                .toList();

        if (!invalid.isEmpty()) {
            throw new LicenseException(ErrorCode.INVALID_ENTITLEMENT_KEY,
                    "제품 '" + productCode + "' 에 등록되지 않은 entitlement 키: " + invalid
                            + " (허용: " + entitlementRegistry.allowedKeys(productCode) + ")");
        }
    }

    /**
     * 제품 ID → 제품 코드 해석. 제품을 찾지 못하면 INVALID_REQUEST.
     */
    private String resolveProductCode(UUID productId) {
        if (productId == null) {
            throw new LicenseException(ErrorCode.INVALID_REQUEST, "productId 가 필요합니다");
        }
        return productRepository.findById(productId)
                .map(Product::getCode)
                .orElseThrow(() -> new LicenseException(ErrorCode.INVALID_REQUEST,
                        "존재하지 않는 제품입니다: " + productId));
    }

    /**
     * 플랜 활성화.
     */
    public LicensePlanResponse activatePlan(UUID id) {
        LicensePlan plan = planRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        plan.activate();
        return LicensePlanResponse.fromEntity(plan);
    }

    /**
     * 플랜 비활성화.
     */
    public LicensePlanResponse deactivatePlan(UUID id) {
        LicensePlan plan = planRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        plan.deactivate();
        return LicensePlanResponse.fromEntity(plan);
    }

    /**
     * 플랜 삭제 (soft delete).
     */
    public void deletePlan(UUID id) {
        LicensePlan plan = planRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        plan.delete();
    }
}
