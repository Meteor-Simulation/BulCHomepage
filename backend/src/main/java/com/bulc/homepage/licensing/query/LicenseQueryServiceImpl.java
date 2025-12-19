package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LicenseQueryServiceImpl implements LicenseQueryService {

    private final LicenseQueryRepository queryRepository;

    @Override
    public LicenseDetailView getById(UUID licenseId) {
        return queryRepository.findDetailById(licenseId)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));
    }

    @Override
    public LicenseDetailView getByKey(String licenseKey) {
        return queryRepository.findDetailByKey(licenseKey)
                .orElseThrow(() -> new LicenseException(ErrorCode.LICENSE_NOT_FOUND));
    }

    @Override
    public List<LicenseSummaryView> findByOwner(OwnerType ownerType, UUID ownerId) {
        return queryRepository.findByOwner(ownerType, ownerId);
    }

    @Override
    public Page<LicenseSummaryView> search(LicenseSearchCond cond, Pageable pageable) {
        return queryRepository.search(cond, pageable);
    }
}
