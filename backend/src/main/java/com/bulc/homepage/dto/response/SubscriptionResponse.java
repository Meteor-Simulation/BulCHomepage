package com.bulc.homepage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private Long id;
    private String productCode;
    private String productName;
    private Long pricePlanId;
    private String pricePlanName;
    private BigDecimal price;
    private String currency;
    private String status;           // A: Active, E: Expired, C: Canceled
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew;
    private String billingCycle;     // MONTHLY, QUARTERLY, YEARLY
    private LocalDateTime nextBillingDate;
    private LocalDateTime createdAt;

    /**
     * 상태 표시 텍스트 반환
     */
    public String getStatusText() {
        switch (status) {
            case "A":
                return "활성";
            case "E":
                return "만료됨";
            case "C":
                return "취소됨";
            default:
                return status;
        }
    }

    /**
     * 결제 주기 표시 텍스트 반환
     */
    public String getBillingCycleText() {
        if (billingCycle == null) return null;
        switch (billingCycle) {
            case "MONTHLY":
                return "월간";
            case "QUARTERLY":
                return "분기별";
            case "YEARLY":
                return "연간";
            default:
                return billingCycle;
        }
    }
}
