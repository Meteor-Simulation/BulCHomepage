package com.bulc.homepage.controller;

import com.bulc.homepage.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    @Value("${mail.from.accounts:support@msimul.com}")
    private String supportEmail;

    @Value("${mail.reply-to:juwon@msimul.com}")
    private String replyToEmail;

    /**
     * 문의하기 API
     */
    @PostMapping
    public ResponseEntity<?> submitContact(@RequestBody ContactRequest request) {
        // 유효성 검사
        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이메일을 입력해주세요."
            ));
        }
        if (request.subject() == null || request.subject().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "문의 제목을 입력해주세요."
            ));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "문의 내용을 입력해주세요."
            ));
        }

        try {
            // 관리자에게 보내는 이메일 내용
            String htmlContent = buildContactEmailHtml(request);

            // 카테고리 처리 (기본값: METEOR)
            String category = request.category() != null && !request.category().isBlank()
                    ? request.category()
                    : "METEOR";

            // 문의 이메일 발송
            emailService.sendEmail(
                    supportEmail,  // from
                    replyToEmail,  // to (관리자 이메일)
                    "[" + category + " 문의] " + request.subject(),
                    htmlContent
            );

            log.info("문의 접수 - 이메일: {}, 제목: {}", request.email(), request.subject());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "문의가 성공적으로 전송되었습니다."
            ));
        } catch (Exception e) {
            log.error("문의 이메일 발송 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "문의 전송에 실패했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
    }

    private String buildContactEmailHtml(ContactRequest request) {
        // 카테고리 처리 (기본값: METEOR)
        String category = request.category() != null && !request.category().isBlank()
                ? request.category()
                : "METEOR";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Malgun Gothic', sans-serif; line-height: 1.8; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .title { font-size: 18px; font-weight: bold; color: #C4320A; margin-bottom: 20px; }
                        .field { margin-bottom: 12px; }
                        .label { font-weight: bold; }
                        .message { white-space: pre-wrap; margin-top: 8px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="title">%s 문의</div>
                        <div class="field"><span class="label">이메일 : </span>%s</div>
                        <div class="field"><span class="label">문의 제목 : </span>%s</div>
                        <div class="field">
                            <span class="label">문의 내용 : </span>
                            <div class="message">%s</div>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(category),
                escapeHtml(request.email()),
                escapeHtml(request.subject()),
                escapeHtml(request.message())
        );
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record ContactRequest(String email, String subject, String message, String category) {}
}
