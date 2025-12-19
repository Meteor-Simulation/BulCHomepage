package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Product 조회용 Repository (licensing 모듈에서 사용).
 * productCode로 productId를 조회할 때 사용합니다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 제품 코드로 조회.
     */
    Optional<Product> findByCode(String code);

    /**
     * 활성화된 제품 중 코드로 조회.
     */
    Optional<Product> findByCodeAndIsActiveTrue(String code);
}
