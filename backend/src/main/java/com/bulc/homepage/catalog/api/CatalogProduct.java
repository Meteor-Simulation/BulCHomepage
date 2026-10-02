package com.bulc.homepage.catalog.api;

import java.util.UUID;

/**
 * 상품의 식별 정보만 담은 공개 표현 (MDP-934).
 *
 * <p>바깥(라이선싱 등)이 상품에서 실제로 필요한 것은 <b>누구인지(id)</b>와
 * <b>어떻게 부르는지(code · name)</b> 뿐이다. 실측해 보니 호출부 5곳 전부가 이 셋만 썼다.
 *
 * <p>{@code Product} 엔티티를 그대로 넘기지 않는 이유: 활성 여부·설명·타임스탬프가 함께
 * 나가고, 카탈로그가 필드를 하나 고칠 때마다 소비자가 따라 깨진다. 더 중요하게는 JPA 영속
 * 객체가 모듈 밖으로 나가면 바깥에서 수정이 가능해져 "상품은 카탈로그가 관리한다" 가 무너진다.
 */
public record CatalogProduct(UUID id, String code, String name) {
}
