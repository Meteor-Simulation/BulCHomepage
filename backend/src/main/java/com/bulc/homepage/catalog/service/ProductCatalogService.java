package com.bulc.homepage.catalog.service;

import com.bulc.homepage.catalog.api.CatalogProduct;
import com.bulc.homepage.catalog.api.ProductCatalogPort;
import com.bulc.homepage.catalog.domain.Product;
import com.bulc.homepage.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ProductCatalogPort} 구현 (MDP-934).
 *
 * <p>엔티티를 {@link CatalogProduct} 로 좁혀 내보내는 것이 이 클래스의 전부다. 얇은 것이 맞다 —
 * 상품 조회에 업무 규칙이 없고, 있어야 할 규칙(활성 여부를 가릴지)은 메서드 선택으로 표현된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService implements ProductCatalogPort {

    private final ProductRepository productRepository;

    @Override
    public Optional<CatalogProduct> findById(UUID productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return productRepository.findById(productId).map(ProductCatalogService::toView);
    }

    @Override
    public Optional<CatalogProduct> findActiveByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return productRepository.findByCodeAndIsActiveTrue(code).map(ProductCatalogService::toView);
    }

    private static CatalogProduct toView(Product p) {
        return new CatalogProduct(p.getId(), p.getCode(), p.getName());
    }
}
