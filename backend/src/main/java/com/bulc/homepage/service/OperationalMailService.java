package com.bulc.homepage.service;

import com.bulc.homepage.email.EmailCategory;
import com.bulc.homepage.entity.EmailLog;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.EmailLogRepository;
import com.bulc.homepage.repository.LeadContactRepository;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static final String LICENSE_EXPIRY_TEMPLATE = "license_expiry";

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final LeadContactRepository leadContactRepository;
    private final EmailLogRepository emailLogRepository;

    /** 발송 결과 요약 (대상 수 / 성공 / 실패). */
    public record MailSendResult(int targetCount, int sentCount, int failedCount) {}

    /**
     * 일반 운영 안내 발송 (프로그램 업데이트 / 약관 변경 / 보안 공지 공통 진입점).
     *
     * <p>안내성(OPERATIONAL) 카테고리이므로 회원 marketing_agreed 와 무관하게 발송된다.
     * 단, 직접등록 컨택은 안내성 수신동의(optInTransactional) + 미해지 건만 대상으로 한다.
     *
     * @param title             메일 본문 큰 제목
     * @param contentHtml       본문 (HTML 허용)
     * @param subject           메일 Subject 헤더
     * @param templateKey       email_log 분류 키 (program_update, terms_change, security_notice 등)
     * @param includeMembers    활성 회원(User) 전체를 대상에 포함
     * @param includeContacts   미해지+안내성 동의 컨택(LeadContact)을 대상에 포함
     * @param explicitRecipients 직접 지정한 이메일(선택). 위 소스와 합쳐 중복 제거된다.
     * @return 대상/성공/실패 건수
     */
    public MailSendResult sendOperationalNotice(String title, String contentHtml, String subject,
                                                String templateKey, boolean includeMembers,
                                                boolean includeContacts, List<String> explicitRecipients) {
        List<String> targets = resolveRecipients(includeMembers, includeContacts, explicitRecipients);
        Map<String, String> vars = new HashMap<>();
        vars.put("title", title);
        vars.put("content", contentHtml);

        // 운영 안내는 항상 operational_notice 템플릿 사용 (file) — templateKey 는 분류용
        String html = emailService.renderTemplate("operational_notice", vars);

        int sent = 0;
        int failed = 0;
        for (String email : targets) {
            try {
                emailService.send(EmailCategory.OPERATIONAL, email, templateKey, subject, html);
                sent++;
            } catch (Exception e) {
                failed++;
                // 단일 수신자 실패해도 다른 수신자에게 계속 진행
                // (EmailService 에서 email_log FAILED 로 이미 기록됨)
                log.warn("운영성 메일 발송 실패 - {} / {} / 사유: {}", templateKey, email, e.getMessage());
            }
        }
        log.info("운영성 메일 발송 완료: templateKey={}, 대상={}명, 성공={}건, 실패={}건",
                templateKey, targets.size(), sent, failed);
        return new MailSendResult(targets.size(), sent, failed);
    }

    /**
     * 라이선스 만료 임박 알림 발송 (LicenseExpiryNotificationScheduler 에서 호출).
     *
     * MDP-505: 동일 사용자에게 같은 날 license_expiry 알림이 이미 SUCCESS 로 발송됐다면 SKIP.
     * 스케줄러 재실행 / 장애 복구 시 중복 발송 방지.
     */
    public void sendLicenseExpiryNotice(String toEmail, String planName,
                                        LocalDate validUntil, long daysRemaining,
                                        String renewUrl) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        boolean alreadySent = emailLogRepository
                .existsByRecipientEmailAndTemplateKeyAndStatusAndSentAtBetween(
                        toEmail, LICENSE_EXPIRY_TEMPLATE,
                        EmailLog.Status.SUCCESS, startOfDay, endOfDay);
        if (alreadySent) {
            log.info("라이선스 만료 알림 중복 SKIP (오늘 이미 발송): {}", toEmail);
            return;
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("plan_name", planName != null ? planName : "BUL:C");
        vars.put("valid_until", validUntil != null ? validUntil.format(DATE_FMT) : "-");
        vars.put("days_remaining", String.valueOf(daysRemaining));
        vars.put("renew_url", renewUrl);

        String subject = String.format("[BulC] 라이선스 만료 D-%d 안내", daysRemaining);
        emailService.sendByTemplate(EmailCategory.OPERATIONAL, toEmail,
                LICENSE_EXPIRY_TEMPLATE, subject, vars);
    }

    /**
     * 발송 대상 이메일을 소스별로 모아 중복 제거(대소문자 무시)하여 반환.
     */
    private List<String> resolveRecipients(boolean includeMembers, boolean includeContacts,
                                           List<String> explicitRecipients) {
        Map<String, String> dedup = new LinkedHashMap<>();
        if (includeMembers) {
            userRepository.findAllByIsActiveTrue().forEach(u -> addEmail(dedup, u.getEmail()));
        }
        if (includeContacts) {
            leadContactRepository.findActiveTransactionalEmails().forEach(e -> addEmail(dedup, e));
        }
        if (explicitRecipients != null) {
            explicitRecipients.forEach(e -> addEmail(dedup, e));
        }
        return new ArrayList<>(dedup.values());
    }

    /** 이메일을 소문자 키로 중복 제거하여 추가 (원본 표기는 보존). */
    private void addEmail(Map<String, String> dedup, String email) {
        if (email == null) return;
        String trimmed = email.trim();
        if (trimmed.isEmpty()) return;
        dedup.putIfAbsent(trimmed.toLowerCase(), trimmed);
    }
}
