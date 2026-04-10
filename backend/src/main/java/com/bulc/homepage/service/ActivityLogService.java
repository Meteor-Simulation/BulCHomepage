package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.ActivityLogRequest;
import com.bulc.homepage.entity.ActivityLog;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.ActivityLogRepository;
import com.bulc.homepage.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void logActivity(ActivityLogRequest request, HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser();

            ActivityLog activityLog = ActivityLog.builder()
                    .userId(user != null ? user.getId() : null)
                    .action(request.getAction())
                    .targetType(request.getResourcePath() != null ? "resource" : null)
                    .description(buildDescription(request, httpRequest))
                    .ipAddress(getClientIpAddress(httpRequest))
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .build();

            activityLogRepository.save(activityLog);
            log.debug("Activity logged: {} - {}", request.getAction(), request.getResourcePath());
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }

    @Transactional
    public void logActivitySync(String action, String resourcePath, String httpMethod, HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser();

            ActivityLog activityLog = ActivityLog.builder()
                    .userId(user != null ? user.getId() : null)
                    .action(action)
                    .targetType("resource")
                    .description(String.format("%s %s", httpMethod, resourcePath))
                    .ipAddress(getClientIpAddress(httpRequest))
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .build();

            activityLogRepository.save(activityLog);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }

    @Transactional
    public void logLoginActivity(User user, HttpServletRequest httpRequest, boolean success) {
        String action = success ? "login" : "login_failed";

        ActivityLog activityLog = ActivityLog.builder()
                .userId(user != null ? user.getId() : null)
                .action(action)
                .targetType("user")
                .targetId(null)
                .description(success ? "로그인 성공" : "로그인 실패")
                .ipAddress(getClientIpAddress(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        activityLogRepository.save(activityLog);
    }

    /**
     * 결제 활동 로그 (성공/실패 모두 기록)
     */
    @Async
    @Transactional
    public void logPaymentActivity(UUID userId, String orderId, String status,
                                    String description, String ipAddress, String userAgent) {
        try {
            String action = "DONE".equals(status) ? "payment_success" : "payment_failed";

            ActivityLog activityLog = ActivityLog.builder()
                    .userId(userId)
                    .action(action)
                    .targetType("payment")
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            activityLogRepository.save(activityLog);
        } catch (Exception e) {
            log.error("결제 활동 로그 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 비정상 접근 로그 (인증 실패, 권한 없음 등)
     */
    @Async
    @Transactional
    public void logSecurityEvent(UUID userId, String action, String description,
                                  String ipAddress, String userAgent) {
        try {
            ActivityLog activityLog = ActivityLog.builder()
                    .userId(userId)
                    .action(action)
                    .targetType("security")
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            activityLogRepository.save(activityLog);
        } catch (Exception e) {
            log.error("보안 이벤트 로그 저장 실패: {}", e.getMessage());
        }
    }

    @Transactional
    public void logSignupActivity(User user, HttpServletRequest httpRequest) {
        ActivityLog activityLog = ActivityLog.builder()
                .userId(user.getId())
                .action("signup")
                .targetType("user")
                .targetId(null)
                .description("회원가입 완료")
                .ipAddress(getClientIpAddress(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        activityLogRepository.save(activityLog);
    }

    private String buildDescription(ActivityLogRequest request, HttpServletRequest httpRequest) {
        StringBuilder sb = new StringBuilder();
        if (request.getHttpMethod() != null) {
            sb.append(request.getHttpMethod()).append(" ");
        }
        if (request.getResourcePath() != null) {
            sb.append(request.getResourcePath());
        }
        if (request.getMetadata() != null) {
            sb.append(" - Metadata: ").append(request.getMetadata());
        }
        return sb.toString();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            // authentication.getName()은 이제 userId.toString()
            String userIdString = authentication.getName();
            try {
                UUID userId = UUID.fromString(userIdString);
                return userRepository.findById(userId).orElse(null);
            } catch (IllegalArgumentException e) {
                // 하위 호환성: email로 시도
                return userRepository.findByEmail(userIdString).orElse(null);
            }
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
