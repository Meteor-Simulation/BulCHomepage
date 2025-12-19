package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * 라이선스 조회 전용 서비스 (Read Model)
 */
public interface LicenseQueryService {

    /**
     * ID로 라이선스 상세 조회
     *
     * @param licenseId 라이선스 ID
     * @return 라이선스 상세 정보
     * @throws com.bulc.homepage.licensing.exception.LicenseException LICENSE_NOT_FOUND
     */
    LicenseDetailView getById(UUID licenseId);

    /**
     * 라이선스 키로 상세 조회
     *
     * @param licenseKey 라이선스 키
     * @return 라이선스 상세 정보
     * @throws com.bulc.homepage.licensing.exception.LicenseException LICENSE_NOT_FOUND
     */
    LicenseDetailView getByKey(String licenseKey);

    /**
     * 소유자별 라이선스 목록 조회
     *
     * @param ownerType 소유자 타입 (USER, ORGANIZATION)
     * @param ownerId   소유자 ID
     * @return 라이선스 목록
     */
    List<LicenseSummaryView> findByOwner(OwnerType ownerType, UUID ownerId);

    /**
     * 조건 기반 라이선스 검색 (페이징)
     *
     * @param cond     검색 조건
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    Page<LicenseSummaryView> search(LicenseSearchCond cond, Pageable pageable);
}
