package com.bulc.homepage.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

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
        String content = buildVerificationEmailContent(verificationCode);
        sendEmail(mailFromAccounts, toEmail, subject, content);
    }

    /**
     * 비밀번호 재설정 코드 발송
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        String subject = "[BulC] 비밀번호 재설정 코드";
        String content = buildPasswordResetEmailContent(resetCode);
        sendEmail(mailFromAccounts, toEmail, subject, content);
    }

    /**
     * 결제 관련 이메일 발송
     */
    public void sendBillingEmail(String toEmail, String subject, String content) {
        sendEmail(mailFromBilling, toEmail, subject, content);
    }

    /**
     * 일반 이메일 발송
     */
    public void sendEmail(String fromEmail, String toEmail, String subject, String htmlContent) {
        if (!isConfigured) {
            log.info("[이메일 미설정] To: {}, Subject: {}", toEmail, subject);
            log.debug("[이메일 내용]\n{}", htmlContent);
            throw new RuntimeException("이메일 서비스가 설정되지 않았습니다. (" + (initError != null ? initError : "환경변수 확인 필요") + ")");
        }

        try {
            Message message = new Message();
            message.setSubject(subject);

            ItemBody body = new ItemBody();
            body.setContentType(BodyType.Html);
            body.setContent(htmlContent);
            message.setBody(body);

            Recipient toRecipient = new Recipient();
            EmailAddress toAddress = new EmailAddress();
            toAddress.setAddress(toEmail);
            toRecipient.setEmailAddress(toAddress);
            message.setToRecipients(List.of(toRecipient));

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
            log.info("이메일 발송 성공: {} -> {}", fromEmail, toEmail);
        } catch (Exception e) {
            log.error("이메일 발송 실패: {} -> {}, 오류: {}", fromEmail, toEmail, e.getMessage(), e);

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
     * 기본 발신자로 이메일 발송
     */
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        sendEmail(mailFrom, toEmail, subject, htmlContent);
    }

    /**
     * 인증 코드 이메일 HTML 템플릿
     */
    private String buildVerificationEmailContent(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Noto Sans KR', sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
                    .card { background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                    .logo { text-align: center; margin-bottom: 30px; }
                    .logo h1 { color: #FF6B00; font-size: 28px; margin: 0; }
                    .title { font-size: 20px; font-weight: 600; color: #333; margin-bottom: 20px; text-align: center; }
                    .message { color: #666; line-height: 1.6; margin-bottom: 30px; text-align: center; }
                    .code-box { background: #f8f9fa; border: 2px dashed #FF6B00; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0; }
                    .code { font-size: 32px; font-weight: 700; color: #FF6B00; letter-spacing: 8px; }
                    .note { font-size: 13px; color: #999; text-align: center; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="card">
                        <div class="logo">
                            <h1>BUL:C</h1>
                        </div>
                        <div class="title">이메일 인증</div>
                        <div class="message">
                            아래 인증 코드를 입력하여<br>이메일 인증을 완료해 주세요.
                        </div>
                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>
                        <div class="note">
                            * 인증 코드는 10분간 유효합니다.<br>
                            * 본인이 요청하지 않은 경우 이 메일을 무시해 주세요.
                        </div>
                    </div>
                    <div class="footer">
                        &copy; 2024 BulC. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code);
    }

    /**
     * 비밀번호 재설정 이메일 HTML 템플릿
     */
    private String buildPasswordResetEmailContent(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Noto Sans KR', sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
                    .card { background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                    .logo { text-align: center; margin-bottom: 30px; }
                    .logo h1 { color: #FF6B00; font-size: 28px; margin: 0; }
                    .title { font-size: 20px; font-weight: 600; color: #333; margin-bottom: 20px; text-align: center; }
                    .message { color: #666; line-height: 1.6; margin-bottom: 30px; text-align: center; }
                    .code-box { background: #f8f9fa; border: 2px dashed #FF6B00; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0; }
                    .code { font-size: 32px; font-weight: 700; color: #FF6B00; letter-spacing: 8px; }
                    .note { font-size: 13px; color: #999; text-align: center; margin-top: 20px; }
                    .warning { font-size: 13px; color: #e74c3c; text-align: center; margin-top: 15px; }
                    .footer { text-align: center; margin-top: 30px; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="card">
                        <div class="logo">
                            <h1>BUL:C</h1>
                        </div>
                        <div class="title">비밀번호 재설정</div>
                        <div class="message">
                            아래 인증 코드를 입력하여<br>비밀번호를 재설정해 주세요.
                        </div>
                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>
                        <div class="note">
                            * 인증 코드는 10분간 유효합니다.
                        </div>
                        <div class="warning">
                            * 본인이 요청하지 않은 경우, 계정 보안을 확인해 주세요.
                        </div>
                    </div>
                    <div class="footer">
                        &copy; 2024 BulC. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code);
    }
}
