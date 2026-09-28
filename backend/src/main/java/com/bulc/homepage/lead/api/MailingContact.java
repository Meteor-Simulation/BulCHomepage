package com.bulc.homepage.lead.api;

/**
 * 광고성 메일 발송에 필요한 최소 정보 (MDP-907).
 *
 * <p>발송 측이 컨택에게서 실제로 필요한 것은 <b>보낼 주소</b>와 <b>수신거부 링크에 넣을 토큰</b>
 * 둘뿐이다. {@code LeadContact} 엔티티를 그대로 넘기면 이름·소속·유입경로 같은 개인정보가
 * 모듈 밖으로 함께 나가고, 필드를 하나 고칠 때마다 발송 측이 따라 깨진다.
 *
 * <p>토큰은 문자열로 넘긴다. 수신거부 URL 에 붙는 값이고, 없을 수 있어서(레거시 컨택)
 * 그 경우 빈 문자열이 온다. 발송 측이 UUID 형식을 알아야 할 이유가 없다.
 */
public record MailingContact(String email, String unsubscribeToken) {
}
