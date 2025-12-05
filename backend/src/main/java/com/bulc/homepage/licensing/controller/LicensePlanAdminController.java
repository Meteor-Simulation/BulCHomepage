package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.dto.LicensePlanRequest;
import com.bulc.homepage.licensing.dto.LicensePlanResponse;
import com.bulc.homepage.licensing.service.LicensePlanAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 라이선스 플랜 Admin API.
 * Admin UI에서 플랜을 관리하기 위한 엔드포인트.
 *
 * 모든 엔드포인트는 ADMIN 권한이 필요합니다.
 */
@RestController
@RequestMapping("/api/admin/license-plans")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LicensePlanAdminController {

    private final LicensePlanAdminService service;

    /**
     * 플랜 목록 조회.
     *
     * GET /api/admin/license-plans
     * GET /api/admin/license-plans?activeOnly=true
     * GET /api/admin/license-plans?productId=xxx
     */
    @GetMapping
    public Page<LicensePlanResponse> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) UUID productId
    ) {
        return service.listPlans(pageable, activeOnly, productId);
    }

    /**
     * 플랜 상세 조회.
     *
     * GET /api/admin/license-plans/{id}
     */
    @GetMapping("/{id}")
    public LicensePlanResponse get(@PathVariable UUID id) {
        return service.getPlan(id);
    }

    /**
     * 새 플랜 생성.
     *
     * POST /api/admin/license-plans
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LicensePlanResponse create(@Valid @RequestBody LicensePlanRequest request) {
        return service.createPlan(request);
    }

    /**
     * 플랜 수정.
     *
     * PUT /api/admin/license-plans/{id}
     */
    @PutMapping("/{id}")
    public LicensePlanResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody LicensePlanRequest request) {
        return service.updatePlan(id, request);
    }

    /**
     * 플랜 활성화.
     *
     * PATCH /api/admin/license-plans/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    public LicensePlanResponse activate(@PathVariable UUID id) {
        return service.activatePlan(id);
    }

    /**
     * 플랜 비활성화.
     *
     * PATCH /api/admin/license-plans/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    public LicensePlanResponse deactivate(@PathVariable UUID id) {
        return service.deactivatePlan(id);
    }

    /**
     * 플랜 삭제 (soft delete).
     *
     * DELETE /api/admin/license-plans/{id}
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deletePlan(id);
    }
}
