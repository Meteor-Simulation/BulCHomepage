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

import java.util.List;

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
                log.info("Microsoft Graph 이메일 서비스 초기화 완료");
            } catch (Exception e) {
                log.error("Microsoft Graph 초기화 실패: {}", e.getMessage());
                isConfigured = false;
            }
        } else {
            log.warn("Microsoft Graph 설정이 없습니다. 이메일은 로그로만 출력됩니다.");
            isConfigured = false;
        }
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
            return;
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
            log.error("이메일 발송 실패: {} -> {}, 오류: {}", fromEmail, toEmail, e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
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
