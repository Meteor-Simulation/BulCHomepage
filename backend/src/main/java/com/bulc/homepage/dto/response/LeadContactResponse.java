package com.bulc.homepage.dto.response;

import com.bulc.homepage.entity.LeadContact;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class LeadContactResponse {

    private Long id;
    private String email;
    private String contactName;
    private String companyName;
    private String department;
    private String role;
    private String address;
    private String workPhone;
    private String workFax;
    private String mobilePhone;
    private String sourceEvent;
    private LocalDate sourceDate;
    private String collectedBy;
    private String consentMethod;
    private LocalDate consentDate;
    private String consentEvidence;
    private Boolean optInMarketing;
    private Boolean optInTransactional;
    private String tags;
    private String notes;
    private UUID unsubscribeToken;
    private LocalDateTime unsubscribedAt;
    private String unsubscribeReason;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LeadContactResponse from(LeadContact c) {
        return LeadContactResponse.builder()
                .id(c.getId())
                .email(c.getEmail())
                .contactName(c.getContactName())
                .companyName(c.getCompanyName())
                .department(c.getDepartment())
                .role(c.getRole())
                .address(c.getAddress())
                .workPhone(c.getWorkPhone())
                .workFax(c.getWorkFax())
                .mobilePhone(c.getMobilePhone())
                .sourceEvent(c.getSourceEvent())
                .sourceDate(c.getSourceDate())
                .collectedBy(c.getCollectedBy())
                .consentMethod(c.getConsentMethod())
                .consentDate(c.getConsentDate())
                .consentEvidence(c.getConsentEvidence())
                .optInMarketing(c.getOptInMarketing())
                .optInTransactional(c.getOptInTransactional())
                .tags(c.getTags())
                .notes(c.getNotes())
                .unsubscribeToken(c.getUnsubscribeToken())
                .unsubscribedAt(c.getUnsubscribedAt())
                .unsubscribeReason(c.getUnsubscribeReason())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
