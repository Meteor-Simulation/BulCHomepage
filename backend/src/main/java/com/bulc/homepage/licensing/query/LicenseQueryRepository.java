package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 라이선스 조회 전용 Repository
 */
public interface LicenseQueryRepository {

    /**
     * ID로 라이선스 상세 조회
     */
    Optional<LicenseDetailView> findDetailById(UUID licenseId);

    /**
     * 라이선스 키로 상세 조회
     */
    Optional<LicenseDetailView> findDetailByKey(String licenseKey);

    /**
     * 소유자별 라이선스 목록 조회
     */
    List<LicenseSummaryView> findByOwner(OwnerType ownerType, UUID ownerId);

    /**
     * 조건 기반 라이선스 검색 (페이징)
     */
    Page<LicenseSummaryView> search(LicenseSearchCond cond, Pageable pageable);
}
