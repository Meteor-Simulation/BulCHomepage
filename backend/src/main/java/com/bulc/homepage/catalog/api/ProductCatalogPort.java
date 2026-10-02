package com.bulc.homepage.catalog.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 카탈로그(상품·가격·할인) 모듈의 공개 계약 (MDP-934).
 *
 * <p><b>Product 소유권을 카탈로그로 정한 근거</b> — 상품 정의는 카탈로그의 핵심 개념이다.
 * 그런데 그동안 {@code ProductRepository} 가 {@code licensing/repository/} 안에 있었다.
 * 라이선싱이 상품을 제일 많이 참조했기 때문인데(13곳 중 7곳), 그건 역사의 흔적이지 설계가 아니다.
 *
 * <p>실측해 보니 라이선싱이 상품 엔티티로 하는 일은 작았다. 대부분 {@code productId}(UUID)만
 * 들고 다니고, 엔티티가 필요한 곳은 다섯 군데뿐이었다:
 * <pre>
 *   LicensePlanAdminService  productId → code     (플랜 권한 검증용 상품코드)
 *   LicenseService           productId → code     (같은 용도)
 *   LicenseService           code → id            (발급 요청의 productCode 해석)
 *   RedeemAdminService       productId → name     (캠페인 응답에 상품명 표시)
 *   RedeemService            productId → name     (같은 용도)
 * </pre>
 *
 * <p>그래서 계약이 두 메서드로 끝난다. 소비자가 적지 않고(라이선싱 4개 서비스 + 관리자 컨트롤러)
 * 제공자가 공개하는 형태라 {@code mail/api/MailPort} 와 같은 방향이다.
 */
public interface ProductCatalogPort {

    /**
     * 상품을 식별자로 조회한다.
     *
     * <p>활성·비활성을 가리지 않는다 — 과거에 발급된 라이선스·캠페인이 비활성 상품을 가리킬 수
     * 있고, 그때도 이름은 보여줘야 한다. "지금 팔고 있는가" 와 "그런 상품이 있었는가" 는 다른 질문이다.
     */
    Optional<CatalogProduct> findById(UUID productId);

    /**
     * <b>판매 중인</b> 상품을 코드로 조회한다.
     *
     * <p>여기서는 활성만 돌려준다. 코드로 찾는 상황은 "이 상품으로 발급해 달라" 는 요청을
     * 해석하는 맥락이고, 내려간 상품으로 새로 발급해서는 안 된다.
     */
    Optional<CatalogProduct> findActiveByCode(String code);
}
