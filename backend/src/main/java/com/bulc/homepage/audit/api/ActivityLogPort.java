package com.bulc.homepage.audit.api;

import java.util.UUID;

/**
 * 활동 로그(감사 추적) 모듈의 공개 계약 (MDP-924).
 *
 * <p>감사 로깅은 <b>횡단 관심사</b>다 — 인증·결제·OAuth 가 모두 기록을 남긴다. 그래서
 * 소비자가 특정되지 않고, 제공자가 계약을 공개하는 형태가 맞다({@code mail/api/MailPort} 와 같은 방향).
 *
 * <p>직전까지 소비자들이 {@code ActivityLogRepository} 와 {@code ActivityLog} 엔티티를 직접
 * 들고 썼다. {@code AuthService} 는 엔티티를 손으로 빌드해 저장했다. 그러면 로그 적재 방식을
 * 바꿀 때(예: 별도 저장소로 분리, 비동기 전환) 호출부 전부를 고쳐야 한다.
 *
 * <p>표면을 세 가지로 좁혔다. 실제로 필요했던 것이 그만큼이다.
 */
public interface ActivityLogPort {

    /**
     * 활동을 기록한다. 가장 일반적인 형태다.
     *
     * <p>기록 실패가 업무 흐름을 끊어서는 안 된다 — 로그를 못 남겼다고 로그인·결제를 실패시키면
     * 본말이 전도된다. 구현이 예외를 삼킨다.
     *
     * @param userId      행위자. 로그인 실패처럼 주체를 모를 수 있어 {@code null} 허용
     * @param action      행위 코드 (login · signup · login_failed · token_theft_detected 등)
     * @param targetType  대상 종류 (user · oauth · security 등)
     * @param targetId    대상 식별자. 없으면 {@code null}
     * @param description 사람이 읽을 설명
     */
    void log(UUID userId, String action, String targetType, Long targetId, String description);

    /**
     * 결제 활동을 기록한다.
     *
     * <p>주문번호·상태·금액이라는 고정된 묶음이 있어 일반 {@link #log} 와 따로 둔다.
     * 호출부가 설명 문구를 매번 조립하지 않게 하려는 것이다.
     */
    void logPaymentActivity(UUID userId, String orderId, String status,
                            String description, String ipAddress, String userAgent);

    /**
     * 해당 사용자의 활동 로그를 모두 지운다. <b>회원 탈퇴 정리 전용</b>이다.
     *
     * <p>감사 로그를 지우는 것은 원칙적으로 바람직하지 않지만, 개인정보를 담고 있어
     * 탈퇴 시 삭제가 필요하다. 그래서 "탈퇴 처리" 라는 용도를 계약에 명시해 둔다 —
     * 다른 목적으로 부르면 안 된다.
     */
    void deleteAllForUser(UUID userId);
}
