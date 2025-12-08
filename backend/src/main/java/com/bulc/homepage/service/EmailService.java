package com.bulc.homepage.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from:noreply@bulc.com}")
    private String fromAddress;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * 이메일 인증 코드 발송
     */
    public void sendVerificationEmail(String toEmail, String verificationCode) {
        if (!mailEnabled) {
            log.info("[메일 비활성화] 인증 코드: {} -> {}", toEmail, verificationCode);
            return;
        }

        String subject = "[BulC] 이메일 인증 코드";
        String content = buildVerificationEmailContent(verificationCode);

        sendHtmlEmail(toEmail, subject, content);
    }

    /**
     * HTML 이메일 발송
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("이메일 발송 성공: {}", to);
        } catch (MessagingException e) {
            log.error("이메일 발송 실패: {}", to, e);
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
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
}
