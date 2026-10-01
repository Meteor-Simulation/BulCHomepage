package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 사용 횟수를 <b>원자적으로</b> 1 증가시킨다 (MDP-749).
     *
     * <p>"조회해서 한도를 확인하고 → 1 증가시켜 저장" 하는 방식은 동시 결제에서 한도를 넘는다.
     * 남은 1회를 두 결제가 동시에 통과시키면 둘 다 할인을 받아 {@code usage_count} 가
     * {@code usage_limit} 을 초과한다. 그래서 한도 검사를 UPDATE 의 WHERE 로 옮겨
     * DB 가 판정하게 한다.
     *
     * <p>{@code usage_limit IS NULL} 은 무제한을 뜻하므로 항상 통과시킨다.
     *
     * @return 증가된 행 수. <b>0 이면 한도가 이미 소진된 것</b>이므로 호출부가 결제를 거부해야 한다
     */
    @Modifying
    @Query("""
            UPDATE Promotion p
               SET p.usageCount = p.usageCount + 1,
                   p.updatedAt = CURRENT_TIMESTAMP
             WHERE p.id = :id
               AND (p.usageLimit IS NULL OR p.usageCount < p.usageLimit)
            """)
    int consumeUsage(@Param("id") Long id);
}
