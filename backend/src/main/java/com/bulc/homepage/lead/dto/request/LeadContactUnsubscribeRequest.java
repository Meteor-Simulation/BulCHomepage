package com.bulc.homepage.lead.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LeadContactUnsubscribeRequest {

    @Size(max = 500)
    private String reason;
}
