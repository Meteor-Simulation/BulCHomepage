package com.bulc.homepage.controller;

import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.repository.ProductRepository;
import com.bulc.homepage.repository.PricePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final PricePlanRepository pricePlanRepository;

    /**
     * 활성화된 상품 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .collect(Collectors.toList());

        List<ProductResponse> response = products.stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getDescription()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 상품별 요금제 목록 조회
     */
    @GetMapping("/{code}/plans")
    public ResponseEntity<List<PricePlanResponse>> getPlans(
            @PathVariable String code,
            @RequestParam(defaultValue = "KRW") String currency) {

        List<PricePlan> plans = pricePlanRepository
                .findByProductCodeAndCurrencyAndIsActiveTrueOrderByPriceAsc(code, currency);

        List<PricePlanResponse> response = plans.stream()
                .map(p -> new PricePlanResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPrice().longValue(),
                        p.getCurrency()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Response DTOs
    public record ProductResponse(String code, String name, String description) {}
    public record PricePlanResponse(Long id, String name, String description, Long price, String currency) {}
}
