package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MDP-707: 전시회·세미나 현장에서 방문자가 직접 입력하는 공개 메일링 등록 요청.
 *
 * <p>{@link LeadContactRegisterRequest}(관리자용)와 달리 방문자가 스스로 입력하므로
 * 수집 항목을 최소화하고, 동의 여부를 요청 본문에서 직접 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class LeadContactPublicRequest {

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 100)
    private String contactName;

    @Size(max = 100)
    private String companyName;

    @Size(max = 100)
    private String department;

    @Size(max = 100)
    private String role;

    @Size(max = 50)
    private String mobilePhone;

    /** 수집 행사명. QR URL 의 {@code ?e=} 파라미터에서 전달된다. */
    @Size(max = 200)
    private String sourceEvent;

    /**
     * 개인정보 수집·이용 동의 (필수). 이용 목적은 무료 배포 코드 발송 및 관련 안내이다.
     *
     * <p>코드 발송은 방문자 본인이 요청한 것을 이행하는 안내성 발송이므로
     * 광고성 정보 수신 동의는 이 폼에서 받지 않는다. 이후 제품 소식·이벤트 등
     * 광고성 발송이 필요하면 코드 안내 메일에서 별도로 동의를 받는다.
     */
    private Boolean agreePrivacy;

    /**
     * 봇 차단용 honeypot. 사람에게는 보이지 않는 입력란이므로 값이 채워져 있으면 봇으로 간주한다.
     */
    @Size(max = 200)
    private String website;

    @AssertTrue(message = "개인정보 수집·이용에 동의해주세요.")
    public boolean isPrivacyAgreed() {
        return Boolean.TRUE.equals(agreePrivacy);
    }
}
