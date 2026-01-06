package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /**
     * 쿠폰 코드로 조회
     */
    Optional<Promotion> findByCode(String code);

    /**
     * 쿠폰 코드로 조회 (대소문자 무시)
     */
    Optional<Promotion> findByCodeIgnoreCase(String code);

    /**
     * 활성화된 프로모션 목록
     */
    List<Promotion> findByIsActiveTrueOrderByCreatedAtDesc();

    /**
     * 전체 목록 (최신순)
     */
    List<Promotion> findAllByOrderByCreatedAtDesc();

    /**
     * 특정 상품에 적용 가능한 프로모션 목록
     */
    List<Promotion> findByProductCodeAndIsActiveTrue(String productCode);

    /**
     * 쿠폰 코드 존재 여부 확인
     */
    boolean existsByCode(String code);

    /**
     * 쿠폰 코드 존재 여부 확인 (대소문자 무시)
     */
    boolean existsByCodeIgnoreCase(String code);
}
