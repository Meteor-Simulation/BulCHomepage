package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.ActivityLogRequest;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserActivityLog;
import com.bulc.homepage.repository.UserActivityLogRepository;
import com.bulc.homepage.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final UserActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void logActivity(ActivityLogRequest request, HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser();

            UserActivityLog activityLog = UserActivityLog.builder()
                    .user(user)
                    .action(request.getAction())
                    .resourcePath(request.getResourcePath())
                    .httpMethod(request.getHttpMethod())
                    .ipAddress(getClientIpAddress(httpRequest))
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .referrer(request.getReferrer())
                    .metadata(request.getMetadata())
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

            UserActivityLog activityLog = UserActivityLog.builder()
                    .user(user)
                    .action(action)
                    .resourcePath(resourcePath)
                    .httpMethod(httpMethod)
                    .ipAddress(getClientIpAddress(httpRequest))
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .referrer(httpRequest.getHeader("Referer"))
                    .build();

            activityLogRepository.save(activityLog);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }

    @Transactional
    public void logLoginActivity(User user, HttpServletRequest httpRequest, boolean success) {
        String action = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";

        UserActivityLog activityLog = UserActivityLog.builder()
                .user(user)
                .action(action)
                .resourcePath("/api/auth/login")
                .httpMethod("POST")
                .ipAddress(getClientIpAddress(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .referrer(httpRequest.getHeader("Referer"))
                .build();

        activityLogRepository.save(activityLog);
    }

    @Transactional
    public void logSignupActivity(User user, HttpServletRequest httpRequest) {
        UserActivityLog activityLog = UserActivityLog.builder()
                .user(user)
                .action("SIGNUP")
                .resourcePath("/api/auth/signup")
                .httpMethod("POST")
                .ipAddress(getClientIpAddress(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .referrer(httpRequest.getHeader("Referer"))
                .build();

        activityLogRepository.save(activityLog);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String email = authentication.getName();
            return userRepository.findByEmail(email).orElse(null);
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
