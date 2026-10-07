package com.bulc.homepage.catalog.repository;

import com.bulc.homepage.catalog.domain.PricePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricePlanRepository extends JpaRepository<PricePlan, Long> {

    /**
     * 상품 코드로 활성화된 요금제 목록 조회
     */
    List<PricePlan> findByProductCodeAndIsActiveTrueOrderByPriceAsc(String productCode);

    /**
     * 상품 코드와 통화로 활성화된 요금제 목록 조회.
     * 내부 전용({@code is_internal})까지 포함하므로 매니저 이상에게만 쓸 것.
     */
    List<PricePlan> findByProductCodeAndCurrencyAndIsActiveTrueOrderByPriceAsc(String productCode, String currency);

    /**
     * 일반 고객에게 보여줄 요금제 목록 — 내부 전용을 제외한다.
     */
    List<PricePlan> findByProductCodeAndCurrencyAndIsActiveTrueAndIsInternalFalseOrderByPriceAsc(
            String productCode, String currency);
}
