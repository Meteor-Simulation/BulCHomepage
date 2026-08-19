package com.bulc.homepage.payment.repository;

import com.bulc.homepage.payment.domain.PricePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricePlanRepository extends JpaRepository<PricePlan, Long> {

    /**
     * 상품 코드로 활성화된 요금제 목록 조회
     */
    List<PricePlan> findByProductCodeAndIsActiveTrueOrderByPriceAsc(String productCode);

    /**
     * 상품 코드와 통화로 활성화된 요금제 목록 조회
     */
    List<PricePlan> findByProductCodeAndCurrencyAndIsActiveTrueOrderByPriceAsc(String productCode, String currency);
}
