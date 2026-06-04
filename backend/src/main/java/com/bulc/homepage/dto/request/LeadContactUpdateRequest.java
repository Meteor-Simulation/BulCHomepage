package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 부분 업데이트: 모든 필드 옵션.
 * null 이면 기존 값 유지.
 */
@Getter
@Setter
@NoArgsConstructor
public class LeadContactUpdateRequest {

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 100)
    private String contactName;

    @Size(max = 100)
    private String companyName;

    @Size(max = 100)
    private String department;

    @Size(max = 100)
    private String role;

    private String address;

    @Size(max = 50)
    private String workPhone;

    @Size(max = 50)
    private String workFax;

    @Size(max = 50)
    private String mobilePhone;

    @Size(max = 200)
    private String sourceEvent;

    private LocalDate sourceDate;

    @Size(max = 100)
    private String collectedBy;

    @Size(max = 50)
    private String consentMethod;

    private LocalDate consentDate;

    private String consentEvidence;

    private Boolean optInMarketing;

    private Boolean optInTransactional;

    @Size(max = 500)
    private String tags;

    private String notes;
}
