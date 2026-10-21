package com.bulc.homepage.lead.api;

/**
 * 메일 발송에 필요한 컨택 정보 (MDP-907, MDP-1020).
 *
 * <p>{@code LeadContact} 엔티티를 그대로 넘기지 않는다. 유입경로·동의 증빙·메모처럼
 * 발송과 무관한 개인정보까지 모듈 밖으로 나가고, 필드를 하나 고칠 때마다 발송 측이
 * 따라 깨지기 때문이다. 필요한 것만 추려서 내보낸다.
 *
 * <h3>이름·소속을 넣은 이유 (MDP-1020)</h3>
 * 본문의 {@code {name}} · {@code {company}} 자리표시자를 수신자마다 치환하려면
 * 발송 시점에 그 값이 필요하다. 처음 설계가 주소와 토큰만 내보낸 것은 그때
 * 개인화가 없어서 이름이 <b>쓰이지도 않으면서 딸려 나가는</b> 상태였기 때문이다.
 * 지금은 쓰임이 분명하다.
 *
 * <p>다만 성격은 그대로다 — <b>발송 순간에만 쓰이고 저장되지 않는다.</b> 메일 모듈이
 * 이 값을 로그나 테이블에 남기면 개인정보가 두 모듈에 중복 보관된다. 발송 이력
 * ({@code email_log})에는 주소만 남는다.
 *
 * <p>토큰은 문자열로 넘긴다. 수신거부 URL 에 붙는 값이고, 없을 수 있어서(레거시 컨택)
 * 그 경우 빈 문자열이 온다. 발송 측이 UUID 형식을 알아야 할 이유가 없다.
 *
 * @param email            보낼 주소
 * @param unsubscribeToken 수신거부 링크에 넣을 토큰 (없으면 빈 문자열)
 * @param name             수신자 이름 (없으면 {@code null})
 * @param company          수신자 소속 (없으면 {@code null})
 */
public record MailingContact(String email, String unsubscribeToken, String name, String company) {
}
