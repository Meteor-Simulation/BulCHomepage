package com.bulc.homepage.config;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 회원가입 및 로그인 유효성 검증 설정
 *
 * 이 파일에서 아이디, 비밀번호 등의 유효성 검증 규칙을 관리합니다.
 * 규칙을 변경하려면 아래 상수 값을 수정하세요.
 */
@Component
@Getter
public class ValidationConfig {

    // ===============================
    // 아이디 (Username/Email) 설정
    // ===============================

    /** 아이디 최소 길이 */
    public static final int USERNAME_MIN_LENGTH = 4;

    /** 아이디 최대 길이 */
    public static final int USERNAME_MAX_LENGTH = 50;

    /** 아이디에 이메일 형식 필수 여부 (true: 이메일만 허용, false: 일반 아이디도 허용) */
    public static final boolean USERNAME_REQUIRE_EMAIL_FORMAT = false;

    /** 아이디 허용 패턴 (정규식) - 영문, 숫자, 언더스코어, 하이픈, @ . 허용 */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_\\-@.]+$";

    /** 아이디 패턴 오류 메시지 */
    public static final String USERNAME_PATTERN_MESSAGE = "아이디는 영문, 숫자, 특수문자(_-@.)만 사용할 수 있습니다";

    // ===============================
    // 비밀번호 설정
    // ===============================

    /** 비밀번호 최소 길이 */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** 비밀번호 최대 길이 */
    public static final int PASSWORD_MAX_LENGTH = 100;

    /** 영문 포함 필수 여부 */
    public static final boolean PASSWORD_REQUIRE_LETTER = true;

    /** 숫자 포함 필수 여부 */
    public static final boolean PASSWORD_REQUIRE_DIGIT = true;

    /** 특수문자 포함 필수 여부 */
    public static final boolean PASSWORD_REQUIRE_SPECIAL_CHAR = true;

    /** 대문자 포함 필수 여부 */
    public static final boolean PASSWORD_REQUIRE_UPPERCASE = false;

    /** 소문자 포함 필수 여부 */
    public static final boolean PASSWORD_REQUIRE_LOWERCASE = false;

    /** 허용되는 특수문자 목록 */
    public static final String PASSWORD_SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    // ===============================
    // 이름 설정
    // ===============================

    /** 이름 최소 길이 */
    public static final int NAME_MIN_LENGTH = 2;

    /** 이름 최대 길이 */
    public static final int NAME_MAX_LENGTH = 50;

    // ===============================
    // 전화번호 설정
    // ===============================

    /** 전화번호 패턴 (정규식) - 숫자와 하이픈만 허용 */
    public static final String PHONE_PATTERN = "^[0-9\\-]+$";

    /** 전화번호 최소 길이 */
    public static final int PHONE_MIN_LENGTH = 10;

    /** 전화번호 최대 길이 */
    public static final int PHONE_MAX_LENGTH = 20;

    // ===============================
    // 에러 메시지 생성 메서드
    // ===============================

    /**
     * 비밀번호 요구사항 메시지 생성
     */
    public static String getPasswordRequirementMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("비밀번호는 ").append(PASSWORD_MIN_LENGTH).append("자 이상");

        if (PASSWORD_REQUIRE_LETTER || PASSWORD_REQUIRE_DIGIT || PASSWORD_REQUIRE_SPECIAL_CHAR) {
            sb.append(", ");
            boolean first = true;

            if (PASSWORD_REQUIRE_LETTER) {
                sb.append("영문");
                first = false;
            }
            if (PASSWORD_REQUIRE_DIGIT) {
                if (!first) sb.append(", ");
                sb.append("숫자");
                first = false;
            }
            if (PASSWORD_REQUIRE_SPECIAL_CHAR) {
                if (!first) sb.append(", ");
                sb.append("특수문자");
            }
            sb.append(" 포함");
        }

        sb.append("이어야 합니다");
        return sb.toString();
    }
}
