package com.bulc.homepage.lead.api;

import java.util.List;
import java.util.UUID;

/**
 * 리드/컨택 모듈의 공개 계약 (MDP-907).
 *
 * <p><b>제공자가 공개하는 쪽</b>의 계약이다 — {@code mail/api/MailPort} 와 같은 형태.
 * 소비자(메일 발송·수신거부 처리)가 특정 한 곳이 아니고, 무엇보다 이 모듈은
 * 비회원의 이메일·이름·소속을 들고 있어 <b>엔티티가 밖으로 나가지 않는 것 자체가 목적</b>이다.
 * 침해가 나도 폭발 반경이 이 모듈 안에서 끝나야 한다.
 *
 * <p>표면을 일부러 좁게 잡았다. 직전까지 {@code OperationalMailService} 가
 * {@code LeadContactRepository} 를 직접 조회했는데, 실제로 필요했던 것은 아래 두 가지
 * 조회뿐이었다. 관리자 화면용 목록·수정·가져오기는 모듈 내부 컨트롤러가 처리하므로
 * 계약에 넣지 않는다.
 */
public interface LeadContactPort {

    /**
     * 광고성(마케팅) 메일 발송 대상.
     *
     * <p>수신거부하지 않았고 광고성 수신에 동의한 컨택만. 동의 여부 판단은 이 모듈이 하고,
     * 발송 측은 결과 목록만 받는다 — 조건을 발송 측이 알면 정책이 두 곳으로 흩어진다.
     */
    List<MailingContact> findMarketingRecipients();

    /**
     * 안내성(트랜잭션) 메일 발송 대상 이메일.
     *
     * <p>광고가 아닌 운영 공지라서 수신거부 링크가 필요 없고, 그래서 토큰 없이 주소만 준다.
     */
    List<String> findTransactionalRecipients();

    /**
     * 수신거부 토큰으로 컨택의 수신을 해지한다. <b>멱등</b>하다.
     *
     * <p>이미 해지된 컨택이어도 {@code true} 다. 수신거부 링크를 두 번 누른 사람에게
     * "대상을 찾을 수 없다"고 답하면 안 되기 때문이다 — 본인은 분명히 해지된 상태다.
     * 즉 반환값의 의미는 "해지했다"가 아니라 <b>"이 토큰의 컨택이 있고, 해지 상태다"</b> 다.
     *
     * @return 토큰에 해당하는 컨택이 있으면 {@code true}, 없으면 {@code false}
     */
    boolean unsubscribeByToken(UUID token, String reason);
}
