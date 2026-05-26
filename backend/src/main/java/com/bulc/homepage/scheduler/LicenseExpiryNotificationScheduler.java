package com.bulc.homepage.scheduler;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.OperationalMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * MDP-496 라이선스 만료 임박 알림 스케줄러.
 *
 * 매일 KST 09:00 실행. ACTIVE USER 라이선스 중 valid_until 이
 * D-30 (정확히 30일 후 날짜) 또는 D-7 (정확히 7일 후 날짜)에 해당하는 건에 대해
 * OperationalMailService 로 알림 발송.
 *
 * 중복 발송 가드는 후속 이슈로 미룸 (현재는 매일 단일 회 실행 가정).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseExpiryNotificationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LicenseRepository licenseRepository;
    private final LicensePlanRepository licensePlanRepository;
    private final UserRepository userRepository;
    private final OperationalMailService operationalMailService;

    @Value("${mail.site-url:https://bulc.msimul.com}")
    private String siteUrl;

    /** 매일 KST 09:00 (UTC 00:00) 실행. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void notifyExpiryD30() {
        notifyForDaysAhead(30);
    }

    @Scheduled(cron = "0 5 9 * * *", zone = "Asia/Seoul")
    public void notifyExpiryD7() {
        notifyForDaysAhead(7);
    }

    private void notifyForDaysAhead(int daysAhead) {
        LocalDate target = LocalDate.now(KST).plusDays(daysAhead);
        Instant start = target.atStartOfDay(KST).toInstant();
        Instant end = target.plusDays(1).atStartOfDay(KST).toInstant();

        List<License> licenses = licenseRepository
                .findActiveUserLicensesExpiringBetween(start, end);
        if (licenses.isEmpty()) {
            return;
        }

        String renewUrl = siteUrl + "/payment";
        int sent = 0;
        for (License license : licenses) {
            try {
                Optional<User> userOpt = userRepository.findById(license.getOwnerId());
                if (userOpt.isEmpty()) continue;
                User user = userOpt.get();
                if (!Boolean.TRUE.equals(user.getIsActive())) continue;

                String planName = resolvePlanName(license);
                LocalDate validUntil = license.getValidUntil().atZone(KST).toLocalDate();
                long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(KST), validUntil);

                operationalMailService.sendLicenseExpiryNotice(
                        user.getEmail(), planName, validUntil, daysRemaining, renewUrl);
                sent++;
            } catch (Exception e) {
                log.warn("라이선스 만료 알림 발송 실패 license={} 사유={}",
                        license.getId(), e.getMessage());
            }
        }
        log.info("라이선스 만료 D-{} 알림 발송: 대상={}건, 성공={}건",
                daysAhead, licenses.size(), sent);
    }

    private String resolvePlanName(License license) {
        if (license.getPlanId() == null) return "BUL:C";
        return licensePlanRepository.findById(license.getPlanId())
                .map(p -> {
                    try {
                        // LicensePlan 엔티티의 name getter 호출 (리플렉션 없이 인터페이스 노출 없을 시 안전 fallback)
                        var nameMethod = p.getClass().getMethod("getName");
                        Object n = nameMethod.invoke(p);
                        return n != null ? n.toString() : "BUL:C";
                    } catch (Exception e) {
                        return "BUL:C";
                    }
                })
                .orElse("BUL:C");
    }
}
