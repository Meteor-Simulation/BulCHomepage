package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class LeadContactRegisterRequest {

    @NotBlank
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

    /** business_card / booth_signup / verbal / web_form / import */
    @Size(max = 50)
    private String consentMethod;

    private LocalDate consentDate;

    private String consentEvidence;

    private Boolean optInMarketing;

    private Boolean optInTransactional;

    /** 쉼표 구분 */
    @Size(max = 500)
    private String tags;

    private String notes;
}
