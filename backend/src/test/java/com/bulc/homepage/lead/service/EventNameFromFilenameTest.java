package com.bulc.homepage.lead.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업로드 파일명에서 행사명을 뽑는 규칙.
 *
 * <p>명함·방명록을 행사별로 모아 올리는 운용 방식에 맞춘 것이다. 행사명을 열로 넣지 않고
 * 파일명에 적는 편이 자연스럽고 실제로 그렇게 올라온다 — {@code 한국소방기술사회(명함).xlsx}.
 */
@DisplayName("파일명에서 행사명 추출")
class EventNameFromFilenameTest {

    @Test
    @DisplayName("확장자를 뗀 이름을 쓴다")
    void stripsExtension() {
        assertThat(LeadContactService.eventNameFromFilename("한국소방기술사회(명함).xlsx"))
                .isEqualTo("한국소방기술사회(명함)");
    }

    @Test
    @DisplayName("괄호 안의 수집 경로를 그대로 살린다")
    void keepsCollectionSuffix() {
        assertThat(LeadContactService.eventNameFromFilename("한국소방기술사회(방명록).csv"))
                .isEqualTo("한국소방기술사회(방명록)");
    }

    /** 이름에 점이 여러 개면 마지막 것만 확장자다. 앞의 점은 행사명의 일부다. */
    @Test
    @DisplayName("점이 여러 개면 마지막 것만 뗀다")
    void stripsOnlyLastDot() {
        assertThat(LeadContactService.eventNameFromFilename("2026.소방전시회(QR).xlsx"))
                .isEqualTo("2026.소방전시회(QR)");
    }

    /**
     * 일부 브라우저·환경은 파일명 대신 전체 경로를 보낸다.
     * 그대로 두면 행사명에 폴더 경로가 섞인다.
     */
    @Test
    @DisplayName("경로가 섞여 오면 파일명만 남긴다")
    void stripsPath() {
        assertThat(LeadContactService.eventNameFromFilename("C:\\Users\\me\\한국소방기술사회(명함).xlsx"))
                .isEqualTo("한국소방기술사회(명함)");
        assertThat(LeadContactService.eventNameFromFilename("/tmp/행사(QR).csv"))
                .isEqualTo("행사(QR)");
    }

    @Test
    @DisplayName("확장자가 없으면 이름을 그대로 쓴다")
    void noExtension() {
        assertThat(LeadContactService.eventNameFromFilename("한국소방기술사회")).isEqualTo("한국소방기술사회");
    }

    /**
     * 점으로 시작하면 그 점은 확장자 구분이 아니다 — 이름 전체를 남긴다.
     *
     * <p>그래서 {@code .xlsx} 처럼 확장자만 있는 이름도 그대로 돌아온다. 그런 파일명은
     * 실무에서 거의 없고, 막으려고 확장자 목록을 들고 있으면 새 형식마다 손봐야 한다.
     */
    @Test
    @DisplayName("점으로 시작하는 이름은 잘라내지 않는다")
    void leadingDot() {
        assertThat(LeadContactService.eventNameFromFilename(".gitignore")).isEqualTo(".gitignore");
        assertThat(LeadContactService.eventNameFromFilename(".xlsx")).isEqualTo(".xlsx");
    }

    @Test
    @DisplayName("비었거나 공백뿐이면 null — 열 값이나 기존 값을 지우지 않는다")
    void blankIsNull() {
        assertThat(LeadContactService.eventNameFromFilename(null)).isNull();
        assertThat(LeadContactService.eventNameFromFilename("")).isNull();
        assertThat(LeadContactService.eventNameFromFilename("   ")).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백을 다듬는다")
    void trims() {
        assertThat(LeadContactService.eventNameFromFilename("  행사명 (명함) .xlsx  "))
                .isEqualTo("행사명 (명함)");
    }
}
