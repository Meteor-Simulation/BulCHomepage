package com.bulc.homepage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingKeyResponse {

    private Long id;
    private String cardCompany;
    private String cardNumber;
    private String cardType;
    private String ownerType;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
