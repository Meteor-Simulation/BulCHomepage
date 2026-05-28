package com.bulc.homepage.service;

import com.bulc.homepage.email.EmailCategory;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MDP-496 운영성 메일 발송 서비스.
 *
 * 정보성(OPERATIONAL) 카테고리 — marketing_agreed 와 무관하게 발송.
 * 수신자 그룹 선정 + 템플릿 렌더링 + EmailService.send 위임만 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalMailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * 일반 운영 안내 발송 (프로그램 업데이트 / 약관 변경 / 보안 공지 공통 진입점).
     *
     * @param title       메일 본문 큰 제목
     * @param contentHtml 본문 (HTML 허용)
     * @param subject     메일 Subject 헤더
     * @param templateKey email_log 분류 키 (program_update, terms_change, security_notice 등)
     * @param recipients  null 또는 빈 리스트면 활성 사용자 전체로 자동 확장
     * @return 발송 시도 건수
     */
    public int sendOperationalNotice(String title, String contentHtml, String subject,
                                     String templateKey, List<String> recipients) {
        List<String> targets = resolveRecipients(recipients);
        Map<String, String> vars = new HashMap<>();
        vars.put("title", title);
        vars.put("content", contentHtml);

        // 운영 안내는 항상 operational_notice 템플릿 사용 (file) — templateKey 는 분류용
        String html = emailService.renderTemplate("operational_notice", vars);

        int count = 0;
        for (String email : targets) {
            try {
                emailService.send(EmailCategory.OPERATIONAL, email, templateKey, subject, html);
                count++;
            } catch (Exception e) {
                // 단일 수신자 실패해도 다른 수신자에게 계속 진행
                // (EmailService 에서 email_log FAILED 로 이미 기록됨)
                log.warn("운영성 메일 발송 실패 - {} / {} / 사유: {}", templateKey, email, e.getMessage());
            }
        }
        log.info("운영성 메일 발송 완료: templateKey={}, 대상={}명, 성공={}건",
                templateKey, targets.size(), count);
        return count;
    }

    /**
     * 라이선스 만료 임박 알림 발송 (LicenseExpiryNotificationScheduler 에서 호출).
     */
    public void sendLicenseExpiryNotice(String toEmail, String planName,
                                        LocalDate validUntil, long daysRemaining,
                                        String renewUrl) {
        Map<String, String> vars = new HashMap<>();
        vars.put("plan_name", planName != null ? planName : "BUL:C");
        vars.put("valid_until", validUntil != null ? validUntil.format(DATE_FMT) : "-");
        vars.put("days_remaining", String.valueOf(daysRemaining));
        vars.put("renew_url", renewUrl);

        String subject = String.format("[BulC] 라이선스 만료 D-%d 안내", daysRemaining);
        emailService.sendByTemplate(EmailCategory.OPERATIONAL, toEmail,
                "license_expiry", subject, vars);
    }

    private List<String> resolveRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return userRepository.findAllByIsActiveTrue().stream()
                    .map(User::getEmail)
                    .toList();
        }
        return recipients;
    }
}
