package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 자체 카드 입력 폼에서 받은 카드 정보 (MDP-758).
 *
 * <p><b>이 객체는 어떤 경우에도 로그로 남기거나 DB에 저장하지 않는다.</b>
 * 토스 빌링키 발급 호출에만 사용하고 즉시 버린다. toString 도 재정의하지 않는다
 * (Lombok @Data 를 쓰지 않는 이유 — 실수로 전체 필드가 찍히는 것을 막는다).
 */
@Getter
@Setter
@NoArgsConstructor
public class CardDirectRegisterRequest {

    /** 카드번호 (하이픈 제거된 숫자만) */
    @NotBlank(message = "카드번호는 필수입니다")
    @Pattern(regexp = "\\d{13,16}", message = "카드번호 형식이 올바르지 않습니다")
    private String cardNumber;

    /** 유효기간 연도 2자리 (YY) */
    @NotBlank(message = "유효기간은 필수입니다")
    @Pattern(regexp = "\\d{2}", message = "유효기간 형식이 올바르지 않습니다")
    private String expiryYear;

    /** 유효기간 월 2자리 (MM) */
    @NotBlank(message = "유효기간은 필수입니다")
    @Pattern(regexp = "(0[1-9]|1[0-2])", message = "유효기간 형식이 올바르지 않습니다")
    private String expiryMonth;

    /** 생년월일 6자리(YYMMDD) 또는 사업자등록번호 10자리 */
    @NotBlank(message = "생년월일 또는 사업자등록번호는 필수입니다")
    @Pattern(regexp = "\\d{6}|\\d{10}", message = "생년월일(6자리) 또는 사업자등록번호(10자리)를 입력해주세요")
    private String identityNumber;

    /** 카드 비밀번호 앞 2자리 */
    @NotBlank(message = "카드 비밀번호 앞 2자리는 필수입니다")
    @Pattern(regexp = "\\d{2}", message = "카드 비밀번호 앞 2자리를 입력해주세요")
    private String cardPassword;

    /** 이 카드를 기본 결제 수단으로 지정할지 여부 */
    private boolean setAsDefault;

    /** 카드 정보가 로그에 노출되지 않도록 의도적으로 마스킹한다. */
    @Override
    public String toString() {
        return "CardDirectRegisterRequest(masked)";
    }
}
