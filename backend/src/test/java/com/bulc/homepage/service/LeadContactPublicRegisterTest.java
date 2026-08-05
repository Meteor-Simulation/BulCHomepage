package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LeadContactPublicRequest;
import com.bulc.homepage.entity.LeadContact;
import com.bulc.homepage.repository.LeadContactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * MDP-707: 전시회 현장 공개 폼 등록 로직 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeadContactService 공개 폼 등록")
class LeadContactPublicRegisterTest {

    @Mock
    private LeadContactRepository leadContactRepository;

    @InjectMocks
    private LeadContactService leadContactService;

    private LeadContactPublicRequest request(String email, boolean marketing) {
        LeadContactPublicRequest req = new LeadContactPublicRequest();
        req.setEmail(email);
        req.setContactName("홍길동");
        req.setCompanyName("메테오건설");
        req.setMobilePhone("010-1234-5678");
        req.setSourceEvent("2026 소방안전박람회");
        req.setAgreePrivacy(true);
        req.setOptInMarketing(marketing);
        return req;
    }

    private LeadContact captureSaved() {
        ArgumentCaptor<LeadContact> captor = ArgumentCaptor.forClass(LeadContact.class);
        verify(leadContactRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("신규 등록")
    class NewContact {

        @Test
        @DisplayName("이메일을 소문자로 정규화하고 동의 정보를 함께 저장한다")
        void savesNormalizedEmailWithConsent() {
            given(leadContactRepository.findByEmail("hong@example.com")).willReturn(Optional.empty());
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("  Hong@Example.COM ", true), "1.2.3.4", "Mozilla/5.0");

            LeadContact saved = captureSaved();
            assertThat(saved.getEmail()).isEqualTo("hong@example.com");
            assertThat(saved.getConsentMethod()).isEqualTo("web_form");
            assertThat(saved.getConsentDate()).isEqualTo(LocalDate.now());
            assertThat(saved.getOptInMarketing()).isTrue();
            assertThat(saved.getOptInTransactional()).isTrue();
            assertThat(saved.getSourceEvent()).isEqualTo("2026 소방안전박람회");
            assertThat(saved.getSourceDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("동의 증빙에 IP·UA·동의 여부를 남긴다")
        void recordsConsentEvidence() {
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.empty());
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("a@b.com", false), "203.0.113.7", "iPhone");

            LeadContact saved = captureSaved();
            assertThat(saved.getConsentEvidence())
                    .contains("ip=203.0.113.7")
                    .contains("ua=iPhone")
                    .contains("privacy=agreed")
                    .contains("marketing=declined");
            assertThat(saved.getOptInMarketing()).isFalse();
        }

        @Test
        @DisplayName("행사명이 없으면 기본값으로 저장한다")
        void fallsBackToDefaultEvent() {
            LeadContactPublicRequest req = request("c@d.com", true);
            req.setSourceEvent(null);
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.empty());
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(req, "1.1.1.1", "ua");

            assertThat(captureSaved().getSourceEvent()).isEqualTo("전시회 현장 등록");
        }
    }

    @Nested
    @DisplayName("이미 등록된 이메일")
    class ExistingContact {

        private LeadContact existing(String email) {
            return LeadContact.builder()
                    .id(1L)
                    .email(email)
                    .contactName("기존이름")
                    .companyName("기존회사")
                    .department("설계팀")
                    .optInMarketing(false)
                    .optInTransactional(true)
                    .notes("[2026-01-01] 이전 행사 현장 등록")
                    .createdBy(UUID.randomUUID())
                    .build();
        }

        @Test
        @DisplayName("예외 없이 기존 컨택에 병합한다")
        void mergesInsteadOfThrowing() {
            given(leadContactRepository.findByEmail("dup@example.com")).willReturn(Optional.of(existing("dup@example.com")));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("dup@example.com", true), "1.1.1.1", "ua");

            LeadContact saved = captureSaved();
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getContactName()).isEqualTo("홍길동");
            assertThat(saved.getCompanyName()).isEqualTo("메테오건설");
        }

        @Test
        @DisplayName("입력하지 않은 항목은 기존 값을 유지한다")
        void keepsExistingValuesWhenBlank() {
            LeadContactPublicRequest req = request("dup@example.com", true);
            req.setCompanyName("");
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.of(existing("dup@example.com")));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(req, "1.1.1.1", "ua");

            LeadContact saved = captureSaved();
            assertThat(saved.getCompanyName()).isEqualTo("기존회사");
            assertThat(saved.getDepartment()).isEqualTo("설계팀");
        }

        @Test
        @DisplayName("참여 행사 이력을 notes 에 누적한다")
        void appendsEventHistory() {
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.of(existing("dup@example.com")));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("dup@example.com", true), "1.1.1.1", "ua");

            assertThat(captureSaved().getNotes())
                    .contains("[2026-01-01] 이전 행사 현장 등록")
                    .contains("2026 소방안전박람회 현장 등록");
        }

        @Test
        @DisplayName("수신 동의를 하지 않으면 기존 동의 상태를 끄지 않는다")
        void doesNotRevokeExistingMarketingConsent() {
            LeadContact contact = existing("dup@example.com");
            contact.setOptInMarketing(true);
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.of(contact));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("dup@example.com", false), "1.1.1.1", "ua");

            assertThat(captureSaved().getOptInMarketing()).isTrue();
        }

        @Test
        @DisplayName("과거 수신거부자가 다시 동의하면 재구독 처리한다")
        void resubscribesWhenPreviouslyUnsubscribed() {
            LeadContact contact = existing("dup@example.com");
            contact.setUnsubscribedAt(LocalDateTime.now().minusDays(30));
            contact.setUnsubscribeReason("관심 없음");
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.of(contact));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("dup@example.com", true), "1.1.1.1", "ua");

            LeadContact saved = captureSaved();
            assertThat(saved.getUnsubscribedAt()).isNull();
            assertThat(saved.getUnsubscribeReason()).isNull();
            assertThat(saved.getOptInMarketing()).isTrue();
        }

        @Test
        @DisplayName("수신거부자가 동의하지 않으면 해지 상태를 유지한다")
        void keepsUnsubscribedWhenNotConsenting() {
            LeadContact contact = existing("dup@example.com");
            LocalDateTime unsubscribedAt = LocalDateTime.now().minusDays(30);
            contact.setUnsubscribedAt(unsubscribedAt);
            given(leadContactRepository.findByEmail(any())).willReturn(Optional.of(contact));
            given(leadContactRepository.save(any(LeadContact.class))).willAnswer(inv -> inv.getArgument(0));

            leadContactService.registerPublic(request("dup@example.com", false), "1.1.1.1", "ua");

            assertThat(captureSaved().getUnsubscribedAt()).isEqualTo(unsubscribedAt);
        }
    }
}
