package com.bulc.homepage.catalog.controller;

import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.catalog.domain.Product;
import com.bulc.homepage.catalog.repository.ProductRepository;
import com.bulc.homepage.catalog.repository.PricePlanRepository;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final PricePlanRepository pricePlanRepository;
    private final UserRepository userRepository;

    /**
     * 매니저 이상(roles_code 000·001) 권한 체크.
     *
     * <p>이 엔드포인트는 비로그인도 접근 가능(permitAll)하므로 인증 여부부터 확인한다.
     */
    private boolean isManagerOrAbove() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        try {
            UUID userId = UUID.fromString(auth.getName());
            return userRepository.findById(userId)
                    .map(user -> "000".equals(user.getRolesCode()) || "001".equals(user.getRolesCode()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 활성화된 상품 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .collect(Collectors.toList());

        List<ProductResponse> response = products.stream()
                .map(p -> new ProductResponse(
                        p.getId() != null ? p.getId().toString() : null,
                        p.getCode(), p.getName(), p.getDescription()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 상품별 요금제 목록 조회.
     *
     * <p>내부 전용 요금제(소액 결제 점검용)는 매니저 이상에게만 내려준다. 목록에서 가리는 것만으로는
     * 부족해서 결제 쪽에서도 같은 검사를 한다 — {@code pricePlanId} 를 알면 결제 API 를 직접
     * 호출할 수 있기 때문이다.
     */
    @GetMapping("/{code}/plans")
    public ResponseEntity<List<PricePlanResponse>> getPlans(
            @PathVariable String code,
            @RequestParam(defaultValue = "KRW") String currency) {

        List<PricePlan> plans = isManagerOrAbove()
                ? pricePlanRepository
                        .findByProductCodeAndCurrencyAndIsActiveTrueOrderByPriceAsc(code, currency)
                : pricePlanRepository
                        .findByProductCodeAndCurrencyAndIsActiveTrueAndIsInternalFalseOrderByPriceAsc(code, currency);

        List<PricePlanResponse> response = plans.stream()
                .map(p -> new PricePlanResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPrice().longValue(),
                        p.getCurrency(),
                        Boolean.TRUE.equals(p.getIsInternal())
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Response DTOs
    public record ProductResponse(String id, String code, String name, String description) {}

    /**
     * @param isInternal 내부 전용 여부. 일반 고객 응답에는 항상 {@code false} 만 들어 있고,
     *                   매니저 이상에게만 {@code true} 가 섞여 내려오므로 화면에서 배지로 구분할 수 있다.
     */
    public record PricePlanResponse(Long id, String name, String description, Long price, String currency,
                                    boolean isInternal) {}
}
