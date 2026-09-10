package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.LicensePlan;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.dto.LicensePlanRequest;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * LicensePlanAdminService 의 entitlement 레지스트리 검증 동작 (MDP-789).
 */
@ExtendWith(MockitoExtension.class)
class LicensePlanAdminServiceEntitlementTest {

    @Mock
    private LicensePlanRepository planRepository;

    @Mock
    private ProductRepository productRepository;

    private EntitlementRegistry registry;
    private LicensePlanAdminService service;

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new EntitlementRegistry();
        registry.setByProduct(Map.of(
                "001", List.of("core-simulation", "export-csv", "advanced-visualization")
        ));
        service = new LicensePlanAdminService(planRepository, productRepository, registry);

        Product product = mock(Product.class);
        lenient().when(product.getCode()).thenReturn("001");
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(planRepository.existsByCodeAndDeletedFalse(any())).thenReturn(false);
        lenient().when(planRepository.save(any(LicensePlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private LicensePlanRequest requestWith(List<String> entitlements) {
        return requestWith(PRODUCT_ID, entitlements);
    }

    private LicensePlanRequest requestWith(UUID productId, List<String> entitlements) {
        return new LicensePlanRequest(
                productId, "PLAN-A", "Plan A", "desc",
                LicenseType.SUBSCRIPTION, 365, 7, 3, 1, 30,
                entitlements
        );
    }

    private LicensePlan existingPlanUnderProduct() {
        return LicensePlan.builder()
                .productId(PRODUCT_ID)
                .code("PLAN-A").name("Plan A").description("desc")
                .licenseType(LicenseType.SUBSCRIPTION)
                .durationDays(365).graceDays(7)
                .maxActivations(3).maxConcurrentSessions(1).allowOfflineDays(30)
                .build();
    }

    @Test
    @DisplayName("정본 어휘만 포함하면 생성 성공")
    void shouldCreatePlanWithValidEntitlements() {
        assertThatCode(() -> service.createPlan(
                requestWith(List.of("core-simulation", "export-csv"))
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("미등록 entitlement 키가 있으면 INVALID_ENTITLEMENT_KEY 로 거부")
    void shouldRejectPlanWithUnregisteredEntitlement() {
        // core-sim 은 클라이언트 어휘 — 서버 정본 아님
        assertThatThrownBy(() -> service.createPlan(
                requestWith(List.of("core-simulation", "core-sim"))
        ))
                .isInstanceOf(LicenseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LicenseException.ErrorCode.INVALID_ENTITLEMENT_KEY);
    }

    @Test
    @DisplayName("레지스트리 미구성이면 검증 생략 (임의 키 허용)")
    void shouldSkipValidationWhenRegistryEmpty() {
        EntitlementRegistry empty = new EntitlementRegistry();
        LicensePlanAdminService unvalidated =
                new LicensePlanAdminService(planRepository, productRepository, empty);

        assertThatCode(() -> unvalidated.createPlan(
                requestWith(List.of("anything-goes"))
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("entitlements 가 비어도 생성 성공")
    void shouldCreatePlanWithNoEntitlements() {
        assertThatCode(() -> service.createPlan(requestWith(List.of())))
                .doesNotThrowAnyException();
    }

    // --- updatePlan 스코프 (리뷰 #244 회귀 커버) ---

    @Test
    @DisplayName("updatePlan: 저장된 plan 의 productId 스코프로 미등록 키 거부")
    void shouldRejectUnregisteredEntitlementOnUpdate() {
        UUID planId = UUID.randomUUID();
        lenient().when(planRepository.findByIdAndDeletedFalse(planId))
                .thenReturn(Optional.of(existingPlanUnderProduct()));

        assertThatThrownBy(() -> service.updatePlan(planId,
                requestWith(List.of("core-simulation", "core-sim"))))
                .isInstanceOf(LicenseException.class)
                .hasFieldOrPropertyWithValue("errorCode", LicenseException.ErrorCode.INVALID_ENTITLEMENT_KEY);
    }

    @Test
    @DisplayName("updatePlan: 요청 body 의 productId 를 다른 값으로 보내도 검증은 저장된 plan 의 productId(001) 기준")
    void shouldValidateAgainstStoredProductIdNotRequestBody() {
        UUID planId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();  // 미등록 제품 — 요청 body 로 스코프를 바꾸려는 시도
        lenient().when(planRepository.findByIdAndDeletedFalse(planId))
                .thenReturn(Optional.of(existingPlanUnderProduct()));

        // 요청 productId 는 otherProductId 이나, 검증은 저장된 plan 의 001 스코프로 수행되므로
        // 001 정본 키(core-simulation)는 통과해야 한다(스코프 불변 계약).
        assertThatCode(() -> service.updatePlan(planId,
                requestWith(otherProductId, List.of("core-simulation"))))
                .doesNotThrowAnyException();
    }
}
