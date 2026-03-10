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
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <!--[if mso]><noscript><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml></noscript><![endif]-->
            </head>
            <body style="margin:0;padding:0;background-color:#ffffff;font-family:'Segoe UI','Noto Sans KR',Arial,sans-serif;">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%" style="background-color:#ffffff;">
                    <tr><td style="padding:40px 16px;">

                        <!-- Main Card -->
                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="540" align="center" style="max-width:540px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                            <!-- Header Bar -->
                            <tr>
                                <td style="background:linear-gradient(135deg,#C4320A 0%%,#E85D04 50%%,#F5A623 100%%);height:6px;font-size:1px;line-height:1px;">&nbsp;</td>
                            </tr>

                            <!-- Logo Section -->
                            <tr>
                                <td style="padding:36px 40px 0;text-align:center;">
                                    <!-- BulC Block Icon -->
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center">
                                        <tr>
                                            <td style="width:10px;height:10px;background:#F5A623;border-radius:2px;" width="10"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:14px;height:14px;background:#E85D04;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:6px;height:6px;" width="6"></td>
                                        </tr>
                                        <tr><td colspan="5" style="height:3px;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                        <tr>
                                            <td style="width:14px;height:14px;background:#E85D04;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:14px;height:14px;background:#C4320A;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:10px;height:10px;background:#D4450E;border-radius:2px;" width="10"></td>
                                        </tr>
                                        <tr><td colspan="5" style="height:3px;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                        <tr>
                                            <td style="width:6px;height:6px;" width="6"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:10px;height:10px;background:#C4320A;border-radius:2px;" width="10"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:6px;height:6px;background:#E85D04;border-radius:2px;" width="6"></td>
                                        </tr>
                                    </table>

                                    <p style="margin:14px 0 0;font-size:24px;font-weight:800;color:#1a1a1a;letter-spacing:2px;">BUL:C</p>
                                </td>
                            </tr>

                            <!-- Divider -->
                            <tr>
                                <td style="padding:20px 40px 0;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%">
                                        <tr><td style="height:1px;background:#f0f0f0;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Title -->
                            <tr>
                                <td style="padding:28px 40px 0;text-align:center;">
                                    <p style="margin:0;font-size:20px;font-weight:700;color:#1a1a1a;">이메일 인증</p>
                                </td>
                            </tr>

                            <!-- Description -->
                            <tr>
                                <td style="padding:12px 40px 0;text-align:center;">
                                    <p style="margin:0;font-size:14px;color:#6b7280;line-height:1.7;">
                                        아래 인증 코드를 입력하여<br>이메일 인증을 완료해 주세요.
                                    </p>
                                </td>
                            </tr>

                            <!-- Code Blocks -->
                            <tr>
                                <td style="padding:28px 40px;text-align:center;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto;">
                                        <tr>
                                            <td style="background:#E85D04;border-radius:12px;padding:18px 36px;text-align:center;">
                                                <span style="font-size:30px;font-weight:800;color:#ffffff;font-family:'Segoe UI',Arial,sans-serif;letter-spacing:10px;">%s</span>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Timer Badge -->
                            <tr>
                                <td style="padding:0 40px;text-align:center;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto;">
                                        <tr>
                                            <td style="background:#fef3cd;border-radius:20px;padding:8px 20px;text-align:center;">
                                                <span style="font-size:13px;color:#92400e;font-weight:600;">&#9202; 인증 코드는 10분간 유효합니다</span>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Note -->
                            <tr>
                                <td style="padding:24px 40px 0;text-align:center;">
                                    <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.7;">
                                        본인이 요청하지 않은 경우 이 메일을 무시해 주세요.<br>
                                        이 코드는 타인과 공유하지 마세요.
                                    </p>
                                </td>
                            </tr>

                            <!-- Bottom Padding -->
                            <tr>
                                <td style="padding:32px 0 0;"></td>
                            </tr>

                            <!-- Footer Bar -->
                            <tr>
                                <td style="background:#f8f9fb;padding:20px 40px;border-top:1px solid #f0f0f0;text-align:center;">
                                    <p style="margin:0;font-size:11px;color:#9ca3af;">
                                        &copy; 2025 MSimul Inc. All rights reserved.<br>
                                        <span style="color:#c4c4c4;">Fire Safety Simulation Platform</span>
                                    </p>
                                </td>
                            </tr>

                        </table>
                        <!-- End Main Card -->

                    </td></tr>
                </table>
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
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <!--[if mso]><noscript><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml></noscript><![endif]-->
            </head>
            <body style="margin:0;padding:0;background-color:#ffffff;font-family:'Segoe UI','Noto Sans KR',Arial,sans-serif;">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%" style="background-color:#ffffff;">
                    <tr><td style="padding:40px 16px;">

                        <!-- Main Card -->
                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="540" align="center" style="max-width:540px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                            <!-- Header Bar -->
                            <tr>
                                <td style="background:linear-gradient(135deg,#C4320A 0%%,#E85D04 50%%,#F5A623 100%%);height:6px;font-size:1px;line-height:1px;">&nbsp;</td>
                            </tr>

                            <!-- Logo Section -->
                            <tr>
                                <td style="padding:36px 40px 0;text-align:center;">
                                    <!-- BulC Block Icon -->
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center">
                                        <tr>
                                            <td style="width:10px;height:10px;background:#F5A623;border-radius:2px;" width="10"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:14px;height:14px;background:#E85D04;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:6px;height:6px;" width="6"></td>
                                        </tr>
                                        <tr><td colspan="5" style="height:3px;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                        <tr>
                                            <td style="width:14px;height:14px;background:#E85D04;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:14px;height:14px;background:#C4320A;border-radius:2px;" width="14"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:10px;height:10px;background:#D4450E;border-radius:2px;" width="10"></td>
                                        </tr>
                                        <tr><td colspan="5" style="height:3px;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                        <tr>
                                            <td style="width:6px;height:6px;" width="6"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:10px;height:10px;background:#C4320A;border-radius:2px;" width="10"></td>
                                            <td style="width:3px;" width="3"></td>
                                            <td style="width:6px;height:6px;background:#E85D04;border-radius:2px;" width="6"></td>
                                        </tr>
                                    </table>

                                    <p style="margin:14px 0 0;font-size:24px;font-weight:800;color:#1a1a1a;letter-spacing:2px;">BUL:C</p>
                                </td>
                            </tr>

                            <!-- Divider -->
                            <tr>
                                <td style="padding:20px 40px 0;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%">
                                        <tr><td style="height:1px;background:#f0f0f0;font-size:1px;line-height:1px;">&nbsp;</td></tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Shield Icon + Title -->
                            <tr>
                                <td style="padding:28px 40px 0;text-align:center;">
                                    <p style="margin:0;font-size:20px;font-weight:700;color:#1a1a1a;">&#128274; 비밀번호 재설정</p>
                                </td>
                            </tr>

                            <!-- Description -->
                            <tr>
                                <td style="padding:12px 40px 0;text-align:center;">
                                    <p style="margin:0;font-size:14px;color:#6b7280;line-height:1.7;">
                                        아래 인증 코드를 입력하여<br>비밀번호를 재설정해 주세요.
                                    </p>
                                </td>
                            </tr>

                            <!-- Code Block -->
                            <tr>
                                <td style="padding:28px 40px;text-align:center;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto;">
                                        <tr>
                                            <td style="background:#E85D04;border-radius:12px;padding:18px 36px;text-align:center;">
                                                <span style="font-size:30px;font-weight:800;color:#ffffff;font-family:'Segoe UI',Arial,sans-serif;letter-spacing:10px;">%s</span>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Timer Badge -->
                            <tr>
                                <td style="padding:0 40px;text-align:center;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto;">
                                        <tr>
                                            <td style="background:#fef3cd;border-radius:20px;padding:8px 20px;text-align:center;">
                                                <span style="font-size:13px;color:#92400e;font-weight:600;">&#9202; 인증 코드는 10분간 유효합니다</span>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Security Warning -->
                            <tr>
                                <td style="padding:20px 40px 0;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%" style="background:#fef2f2;border-radius:10px;border:1px solid #fecaca;">
                                        <tr>
                                            <td style="padding:14px 18px;">
                                                <p style="margin:0;font-size:12px;color:#dc2626;line-height:1.6;font-weight:500;">
                                                    &#9888; 본인이 요청하지 않은 경우, 계정 보안을 즉시 확인해 주세요.<br>
                                                    이 코드는 타인과 절대 공유하지 마세요.
                                                </p>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- Bottom Padding -->
                            <tr>
                                <td style="padding:32px 0 0;"></td>
                            </tr>

                            <!-- Footer Bar -->
                            <tr>
                                <td style="background:#f8f9fb;padding:20px 40px;border-top:1px solid #f0f0f0;text-align:center;">
                                    <p style="margin:0;font-size:11px;color:#9ca3af;">
                                        &copy; 2025 MSimul Inc. All rights reserved.<br>
                                        <span style="color:#c4c4c4;">Fire Safety Simulation Platform</span>
                                    </p>
                                </td>
                            </tr>

                        </table>
                        <!-- End Main Card -->

                    </td></tr>
                </table>
            </body>
            </html>
            """.formatted(code);
    }
}
