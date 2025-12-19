package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.query.LicenseQueryService;
import com.bulc.homepage.licensing.query.LicenseSearchCond;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 관리자용 라이선스 조회 API Controller.
 *
 * 라이선스 검색, 목록 조회 등 관리자 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/admin/licenses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LicenseAdminController {

    private final LicenseQueryService licenseQueryService;

    /**
     * 라이선스 검색 (페이징).
     *
     * GET /api/admin/licenses?ownerType=USER&ownerId={uuid}&status=ACTIVE&...
     */
    @GetMapping
    public ResponseEntity<Page<LicenseSummaryView>> searchLicenses(
            @RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) LicenseStatus status,
            @RequestParam(required = false) LicenseType licenseType,
            @RequestParam(required = false) UsageCategory usageCategory,
            @RequestParam(required = false) String licenseKey,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        LicenseSearchCond cond = LicenseSearchCond.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .productId(productId)
                .planId(planId)
                .status(status)
                .licenseType(licenseType)
                .usageCategory(usageCategory)
                .licenseKey(licenseKey)
                .build();

        return ResponseEntity.ok(licenseQueryService.search(cond, pageable));
    }

    /**
     * 소유자별 라이선스 목록 조회.
     *
     * GET /api/admin/licenses/owner/{ownerType}/{ownerId}
     */
    @GetMapping("/owner/{ownerType}/{ownerId}")
    public ResponseEntity<List<LicenseSummaryView>> getLicensesByOwner(
            @PathVariable OwnerType ownerType,
            @PathVariable UUID ownerId) {
        return ResponseEntity.ok(licenseQueryService.findByOwner(ownerType, ownerId));
    }

    /**
     * 라이선스 상세 조회 (관리자용).
     *
     * GET /api/admin/licenses/{licenseId}
     */
    @GetMapping("/{licenseId}")
    public ResponseEntity<LicenseDetailView> getLicense(@PathVariable UUID licenseId) {
        return ResponseEntity.ok(licenseQueryService.getById(licenseId));
    }
}
