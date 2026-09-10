package com.bulc.homepage.mail.api;

import java.util.Map;

/**
 * 메일 모듈이 다른 모듈에 공개하는 계약 (MDP-848).
 *
 * <p>다른 모듈은 {@code mail.service.EmailService} 같은 구현이 아니라 이 인터페이스에만 의존한다.
 * 메일 모듈을 별도 서비스로 떼어낼 때 바뀌는 것은 이 인터페이스의 구현체뿐이다.
 *
 * <p><b>{@code payment/port/LicenseIssuePort} 와 방향이 반대라는 점에 주의.</b>
 * 그쪽은 <i>소비자</i>인 결제가 "발급자에게 이 정도만 요구한다"고 선언한 계약이라 소비자 패키지에 있다.
 * 메일은 소비자가 7곳이라 소비자마다 포트를 두는 것이 의미가 없으므로,
 * <i>제공자</i>가 "이만큼을 보장한다"고 공개하는 형태로 제공자 패키지({@code mail/api})에 둔다.
 *
 * <p>도메인별 통지 메서드(회원 인증 코드, 라이선스 만료 안내 등)는 이 계약에 넣지 않는다.
 * 그 문구와 시점은 각 도메인의 관심사이므로, 소유 모듈이 템플릿과 함께 들고 있다가
 * 발송만 이 포트에 위임하는 것이 목표 형태다. (현재 일부가 메일 모듈에 남아 있다 — Phase 2)
 */
public interface MailPort {

    /**
     * 렌더링이 끝난 HTML 을 그대로 발송한다.
     *
     * <p>카테고리에 따라 수신 동의 확인·제목 prefix·footer 부착이 적용되며,
     * 모든 시도(SUCCESS / SKIPPED / FAILED)가 발송 이력에 기록된다.
     */
    void send(EmailCategory category, String toEmail, String templateKey,
              String subject, String htmlContent);

    /**
     * 템플릿 키와 치환 변수로 렌더링해 발송한다.
     *
     * @param templateKey {@code resources/templates/mail/<key>.html}
     */
    void sendByTemplate(EmailCategory category, String toEmail, String templateKey,
                        String subject, Map<String, String> vars);

    /**
     * 템플릿을 렌더링만 하고 발송하지 않는다. 미리보기·본문 조합에 쓴다.
     */
    String renderTemplate(String templateKey, Map<String, String> vars);

    /**
     * 발송 설정 진단 정보. 운영 점검용이며 자격증명은 포함하지 않는다.
     */
    Map<String, Object> getDiagnostics();
}
