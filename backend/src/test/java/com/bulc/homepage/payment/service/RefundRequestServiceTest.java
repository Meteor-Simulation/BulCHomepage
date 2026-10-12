package com.bulc.homepage.payment.service;

import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.RefundRequest;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.payment.repository.RefundRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 환불 요청 — 고객 신청과 관리자 처리.
 *
 * <p>고객은 요청만 낸다. 돈이 실제로 움직이는 건 관리자가 승인할 때뿐이므로, 신청·거절
 * 경로에서는 {@link PaymentCancelService} 가 불리지 않았음을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("환불 요청")
class RefundRequestServiceTest {

    private static final Long PAYMENT_ID = 7L;
    private static final Long REQUEST_ID = 3L;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCancelService paymentCancelService;

    @InjectMocks
    private RefundRequestService service;

    private Payment givenPayment(UUID userId, String status) {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setUserId(userId);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("100"));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        return payment;
    }

    private RefundRequest givenRequest(String status) {
        RefundRequest request = RefundRequest.builder()
                .paymentId(PAYMENT_ID)
                .userId(owner)
                .reason("중복 결제")
                .status(status)
                .build();
        request.setId(REQUEST_ID);
        when(refundRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    @Nested
    @DisplayName("고객 신청")
    class CustomerRequest {

        @Test
        @DisplayName("완료된 본인 결제는 신청할 수 있다 — 이 단계에서 돈은 움직이지 않는다")
        void acceptsRequest() {
            givenPayment(owner, "C");

            RefundRequest request = service.request(PAYMENT_ID, owner, "중복 결제라 환불 바랍니다");

            assertThat(request.getStatus()).isEqualTo(RefundRequest.STATUS_PENDING);
            assertThat(request.getReason()).isEqualTo("중복 결제라 환불 바랍니다");
            verify(refundRequestRepository).saveAndFlush(any(RefundRequest.class));
            verifyNoInteractions(paymentCancelService);
        }

        @Test
        @DisplayName("남의 결제에는 신청할 수 없다 — 존재조차 알려주지 않는다")
        void rejectsOthersPayment() {
            givenPayment(owner, "C");

            assertThatThrownBy(() -> service.request(PAYMENT_ID, stranger, "사유"))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    // '권한이 없다' 고 답하면 그 결제가 실재한다는 사실이 새어 나간다
                    .hasMessageContaining("찾을 수 없습니다");

            verify(refundRequestRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"P", "F", "R"})
        @DisplayName("완료되지 않은 결제는 신청할 수 없다 (대기·실패·이미 환불)")
        void rejectsNotCompleted(String status) {
            givenPayment(owner, status);

            assertThatThrownBy(() -> service.request(PAYMENT_ID, owner, "사유"))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    .hasMessageContaining("완료된 결제만");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("사유 없이는 신청할 수 없다 — 관리자가 판단할 근거가 없다")
        void requiresReason(String reason) {
            assertThatThrownBy(() -> service.request(PAYMENT_ID, owner, reason))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    .hasMessageContaining("환불 사유");

            // 사유 검사가 결제 조회보다 먼저다 — 불필요한 조회를 하지 않는다
            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("이미 대기 중인 요청이 있으면 다시 신청할 수 없다 (DB 유니크)")
        void rejectsDuplicatePending() {
            givenPayment(owner, "C");
            when(refundRequestRepository.saveAndFlush(any(RefundRequest.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> service.request(PAYMENT_ID, owner, "사유"))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    .hasMessageContaining("이미 환불 요청이 접수");
        }
    }

    @Nested
    @DisplayName("관리자 처리")
    class AdminProcess {

        @Test
        @DisplayName("승인하면 토스 승인취소를 수행하고 환불 완료로 바꾼다")
        void approveCancelsPayment() {
            RefundRequest request = givenRequest(RefundRequest.STATUS_PENDING);

            service.approve(REQUEST_ID, operator, "확인 후 환불");

            verify(paymentCancelService).cancel(PAYMENT_ID, "확인 후 환불", operator);
            assertThat(request.getStatus()).isEqualTo(RefundRequest.STATUS_APPROVED);
            assertThat(request.getProcessedBy()).isEqualTo(operator);
            assertThat(request.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("메모가 비면 고객 사유를 취소 사유로 넘긴다 — 토스 기록에 빈칸을 두지 않는다")
        void approveFallsBackToCustomerReason() {
            givenRequest(RefundRequest.STATUS_PENDING);

            service.approve(REQUEST_ID, operator, null);

            verify(paymentCancelService).cancel(PAYMENT_ID, "환불 요청 승인: 중복 결제", operator);
        }

        @Test
        @DisplayName("취소가 실패하면 요청을 대기로 남긴다 — '완료' 로 적히면 고객이 받은 줄 안다")
        void approveKeepsPendingWhenCancelFails() {
            RefundRequest request = givenRequest(RefundRequest.STATUS_PENDING);
            when(paymentCancelService.cancel(anyLong(), anyString(), any(UUID.class)))
                    .thenThrow(new PaymentCancelService.PaymentCancelException("환불 계좌 정보가 필요합니다."));

            assertThatThrownBy(() -> service.approve(REQUEST_ID, operator, "메모"))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class);

            assertThat(request.getStatus()).isEqualTo(RefundRequest.STATUS_PENDING);
            assertThat(request.getProcessedAt()).isNull();
            verify(refundRequestRepository, never()).save(any(RefundRequest.class));
        }

        @Test
        @DisplayName("거절하면 돈은 움직이지 않는다")
        void rejectDoesNotTouchMoney() {
            RefundRequest request = givenRequest(RefundRequest.STATUS_PENDING);

            service.reject(REQUEST_ID, operator, "환불 정책 기간 경과");

            assertThat(request.getStatus()).isEqualTo(RefundRequest.STATUS_REJECTED);
            assertThat(request.getAdminNote()).isEqualTo("환불 정책 기간 경과");
            verifyNoInteractions(paymentCancelService);
        }

        @ParameterizedTest
        @ValueSource(strings = {RefundRequest.STATUS_APPROVED, RefundRequest.STATUS_REJECTED})
        @DisplayName("이미 처리된 요청은 다시 처리할 수 없다")
        void rejectsAlreadyProcessed(String status) {
            givenRequest(status);

            assertThatThrownBy(() -> service.approve(REQUEST_ID, operator, "메모"))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    .hasMessageContaining("이미 처리된");

            verifyNoInteractions(paymentCancelService);
        }

        @Test
        @DisplayName("없는 요청 id")
        void notFound() {
            when(refundRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(REQUEST_ID, operator, "메모"))
                    .isInstanceOf(RefundRequestService.RefundRequestException.class)
                    .hasMessageContaining("찾을 수 없습니다");

            verifyNoInteractions(paymentCancelService);
        }
    }
}
