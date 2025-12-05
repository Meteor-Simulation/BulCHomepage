package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.domain.LicensePlan;
import com.bulc.homepage.licensing.dto.LicensePlanRequest;
import com.bulc.homepage.licensing.dto.LicensePlanResponse;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
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
