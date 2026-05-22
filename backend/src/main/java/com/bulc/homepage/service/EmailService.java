package com.bulc.homepage.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.bulc.homepage.email.EmailCategory;
import com.bulc.homepage.entity.EmailLog;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.EmailLogRepository;
import com.bulc.homepage.repository.UserRepository;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final UserRepository userRepository;
    private final EmailLogRepository emailLogRepository;

    @Value("${microsoft.graph.tenant-id:}")
    private String tenantId;

    @Value("${microsoft.graph.client-id:}")
    private String clientId;

    @Value("${microsoft.graph.client-secret:}")
    private String clientSecret;

    @Value("${mail.from:noreply@bulc.co.kr}")
    private String mailFrom;

    @Value("${mail.from-accounts:accounts@bulc.co.kr}")
    private String mailFromAccounts;

    @Value("${mail.from-billing:billing@bulc.co.kr}")
    private String mailFromBilling;

    @Value("${mail.reply-to:support@bulc.co.kr}")
    private String mailReplyTo;

    @Value("${mail.site-url:https://bulc.msimul.com}")
    private String siteUrl;

    @Value("${mail.company.name:주식회사 메테오시뮬레이션}")
    private String companyName;

    @Value("${mail.company.representative:김지태}")
    private String companyRepresentative;

    @Value("${mail.company.business-number:524-88-02647}")
    private String companyBusinessNumber;

    @Value("${mail.company.address:강원특별자치도 원주시 마재2로 10, 305호(원주미래산업진흥원)}")
    private String companyAddress;

    @Value("${mail.company.contact-email:simul@msimul.com}")
    private String companyContactEmail;

    // 템플릿 캐시 (classpath 리소스 1회 로드 후 재사용)
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    private GraphServiceClient graphClient;
    private boolean isConfigured = false;
    private String initError = null;

    @PostConstruct
    public void init() {
        if (tenantId != null && !tenantId.isEmpty() &&
            clientId != null && !clientId.isEmpty() &&
            clientSecret != null && !clientSecret.isEmpty()) {

            try {
                ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                        .tenantId(tenantId)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .build();

                graphClient = new GraphServiceClient(credential, "https://graph.microsoft.com/.default");
                isConfigured = true;
                initError = null;
                log.info("Microsoft Graph 이메일 서비스 초기화 완료");
            } catch (Exception e) {
                log.error("Microsoft Graph 초기화 실패: {}", e.getMessage(), e);
                isConfigured = false;
                initError = "Graph 초기화 실패: " + e.getMessage();
            }
        } else {
            String missing = "";
            if (tenantId == null || tenantId.isEmpty()) missing += "MS_TENANT_ID ";
            if (clientId == null || clientId.isEmpty()) missing += "MS_CLIENT_ID ";
            if (clientSecret == null || clientSecret.isEmpty()) missing += "MS_CLIENT_SECRET ";
            initError = "환경변수 누락: " + missing.trim();
            log.warn("Microsoft Graph 설정이 없습니다. 누락: {}. 이메일은 로그로만 출력됩니다.", missing.trim());
            isConfigured = false;
        }
    }

    /**
     * 이메일 서비스 진단 정보 반환
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", isConfigured);
        info.put("tenantIdSet", tenantId != null && !tenantId.isEmpty());
        info.put("clientIdSet", clientId != null && !clientId.isEmpty());
        info.put("clientSecretSet", clientSecret != null && !clientSecret.isEmpty());
        info.put("fromAccounts", mailFromAccounts);
        info.put("fromBilling", mailFromBilling);
        info.put("replyTo", mailReplyTo);
        if (initError != null) {
            info.put("initError", initError);
        }
        return info;
    }

    /**
     * 이메일 인증 코드 발송
     */
    public void sendVerificationEmail(String toEmail, String verificationCode) {
        String subject = "[BulC] 이메일 인증 코드";
        String content = renderTemplate("verification_code", Map.of("code", verificationCode));
        send(EmailCategory.ACCOUNT, toEmail, "verification_code", subject, content);
    }

    /**
     * 비밀번호 재설정 코드 발송
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        String subject = "[BulC] 비밀번호 재설정 코드";
        String content = renderTemplate("password_reset", Map.of("code", resetCode));
        send(EmailCategory.ACCOUNT, toEmail, "password_reset", subject, content);
    }

    /**
     * 결제 관련 이메일 발송
     */
    public void sendBillingEmail(String toEmail, String subject, String content) {
        send(EmailCategory.TRANSACTION, toEmail, "billing", subject, content);
    }

    /**
     * 카테고리 기반 통합 발송 진입점.
     * - PROMOTIONAL 카테고리는 marketing_agreed=true 인 사용자에게만 발송.
     * - 모든 시도(SUCCESS / SKIPPED / FAILED)를 email_log 에 기록.
     */
    public void send(EmailCategory category, String toEmail, String templateKey,
                     String subject, String htmlContent) {
        // 1. 광고성 메일은 marketing_agreed 체크
        User user = null;
        if (category.requiresMarketingConsent()) {
            Optional<User> userOpt = userRepository.findByEmail(toEmail);
            if (userOpt.isEmpty()) {
                logEmail(toEmail, category, templateKey, EmailLog.Status.SKIPPED,
                        "user_not_found", null);
                log.info("광고성 메일 SKIP (사용자 없음): {} / {}", toEmail, templateKey);
                return;
            }
            user = userOpt.get();
            if (!Boolean.TRUE.equals(user.getMarketingAgreed())) {
                logEmail(toEmail, category, templateKey, EmailLog.Status.SKIPPED,
                        "marketing_agreed=false", null);
                log.info("광고성 메일 SKIP (수신 미동의): {} / {}", toEmail, templateKey);
                return;
            }
        }

        // 2. 제목 prefix + footer 자동 부착
        String finalSubject = category.requiresMarketingConsent()
                ? "(광고) " + subject
                : subject;
        String finalHtml = injectFooter(htmlContent, category, user);

        // 3. 카테고리별 발신 메일박스 선택
        String fromAddr = resolveFromAddress(category);

        // 4. 실제 발송 + 로그
        try {
            sendEmail(fromAddr, toEmail, finalSubject, finalHtml);
            logEmail(toEmail, category, templateKey, EmailLog.Status.SUCCESS, null, null);
        } catch (RuntimeException e) {
            logEmail(toEmail, category, templateKey, EmailLog.Status.FAILED,
                    null, truncate(e.getMessage(), 1000));
            throw e;
        }
    }

    /**
     * 템플릿 본문에 {{footer}} 플레이스홀더가 있으면 카테고리별 footer 로 치환.
     * 없으면 원본 그대로 반환 (기존 sendBillingEmail 같이 외부에서 만든 HTML 호환).
     */
    private String injectFooter(String html, EmailCategory category, User user) {
        if (html == null || !html.contains("{{footer}}")) {
            return html;
        }
        String footerKey = category.requiresMarketingConsent()
                ? "_footer_promotional"
                : "_footer_operational";

        Map<String, String> vars = new HashMap<>();
        vars.put("company.name", companyName);
        vars.put("company.representative", companyRepresentative);
        vars.put("company.businessNumber", companyBusinessNumber);
        vars.put("company.address", companyAddress);
        vars.put("company.contactEmail", companyContactEmail);

        if (category.requiresMarketingConsent()) {
            String token = user != null && user.getUnsubscribeToken() != null
                    ? user.getUnsubscribeToken()
                    : "";
            vars.put("unsubscribe_url", siteUrl + "/unsubscribe?token=" + token);
        }

        String footer = renderTemplate(footerKey, vars);
        return html.replace("{{footer}}", footer);
    }

    /**
     * classpath:templates/mail/<key>.html 로드 + {{var}} 치환.
     * 첫 호출 시 캐시 적재 후 재사용.
     */
    private String renderTemplate(String templateKey, Map<String, String> vars) {
        String tpl = templateCache.computeIfAbsent(templateKey, key -> {
            String path = "templates/mail/" + key + ".html";
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("메일 템플릿 로드 실패: " + path, e);
            }
        });
        String result = tpl;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private String resolveFromAddress(EmailCategory category) {
        return switch (category) {
            case ACCOUNT -> mailFromAccounts;
            case TRANSACTION -> mailFromBilling;
            case OPERATIONAL, PROMOTIONAL -> mailFrom;
        };
    }

    private void logEmail(String recipient, EmailCategory category, String templateKey,
                          EmailLog.Status status, String skipReason, String errorMessage) {
        try {
            emailLogRepository.save(EmailLog.builder()
                    .recipientEmail(recipient)
                    .category(category)
                    .templateKey(templateKey)
                    .status(status)
                    .skipReason(skipReason)
                    .errorMessage(errorMessage)
                    .build());
        } catch (Exception e) {
            log.warn("email_log 저장 실패 (발송 자체는 영향 없음): {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 일반 이메일 발송 (단일 수신자)
     */
    public void sendEmail(String fromEmail, String toEmail, String subject, String htmlContent) {
        sendEmail(fromEmail, List.of(toEmail), subject, htmlContent);
    }

    /**
     * 일반 이메일 발송 (다중 수신자)
     */
    public void sendEmail(String fromEmail, List<String> toEmails, String subject, String htmlContent) {
        if (!isConfigured) {
            log.info("[이메일 미설정] To: {}, Subject: {}", toEmails, subject);
            log.debug("[이메일 내용]\n{}", htmlContent);
            throw new RuntimeException("이메일 서비스가 설정되지 않았습니다. (" + (initError != null ? initError : "환경변수 확인 필요") + ")");
        }

        if (toEmails == null || toEmails.isEmpty()) {
            throw new RuntimeException("수신자 이메일이 비어 있습니다.");
        }

        try {
            Message message = new Message();
            message.setSubject(subject);

            ItemBody body = new ItemBody();
            body.setContentType(BodyType.Html);
            body.setContent(htmlContent);
            message.setBody(body);

            List<Recipient> recipients = toEmails.stream()
                    .map(email -> {
                        Recipient r = new Recipient();
                        EmailAddress addr = new EmailAddress();
                        addr.setAddress(email);
                        r.setEmailAddress(addr);
                        return r;
                    })
                    .toList();
            message.setToRecipients(recipients);

            // Reply-To 설정
            Recipient replyToRecipient = new Recipient();
            EmailAddress replyToAddress = new EmailAddress();
            replyToAddress.setAddress(mailReplyTo);
            replyToRecipient.setEmailAddress(replyToAddress);
            message.setReplyTo(List.of(replyToRecipient));

            SendMailPostRequestBody sendMailRequest = new SendMailPostRequestBody();
            sendMailRequest.setMessage(message);
            sendMailRequest.setSaveToSentItems(true);

            graphClient.users().byUserId(fromEmail).sendMail().post(sendMailRequest);
            log.info("이메일 발송 성공: {} -> {}", fromEmail, toEmails);
        } catch (Exception e) {
            log.error("이메일 발송 실패: {} -> {}, 오류: {}", fromEmail, toEmails, e.getMessage(), e);

            // Graph API 에러 메시지 분류
            String errorMsg = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
            String userMessage;

            if (errorMsg.contains("Authorization") || errorMsg.contains("401") || errorMsg.contains("InvalidAuthenticationToken")) {
                userMessage = "이메일 발송 실패: 인증 오류 (Azure AD Client Secret이 만료되었거나 잘못되었습니다)";
            } else if (errorMsg.contains("403") || errorMsg.contains("Forbidden") || errorMsg.contains("MailboxNotEnabledForRESTAPI")) {
                userMessage = "이메일 발송 실패: 권한 부족 (Azure AD에서 Mail.Send 권한을 확인하세요)";
            } else if (errorMsg.contains("404") || errorMsg.contains("MailboxNotFound") || errorMsg.contains("ResourceNotFound")) {
                userMessage = "이메일 발송 실패: 발신 메일박스를 찾을 수 없습니다 (" + fromEmail + ")";
            } else if (errorMsg.contains("connect") || errorMsg.contains("timeout") || errorMsg.contains("UnknownHost")) {
                userMessage = "이메일 발송 실패: Microsoft Graph API에 연결할 수 없습니다 (네트워크 확인)";
            } else {
                userMessage = "이메일 발송에 실패했습니다: " + errorMsg;
            }

            throw new RuntimeException(userMessage, e);
        }
    }

    /**
     * 기본 발신자로 이메일 발송 (단일 수신자)
     */
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        sendEmail(mailFrom, toEmail, subject, htmlContent);
    }

    /**
     * 기본 발신자로 이메일 발송 (다중 수신자)
     */
    public void sendHtmlEmail(List<String> toEmails, String subject, String htmlContent) {
        sendEmail(mailFrom, toEmails, subject, htmlContent);
    }
}
