package com.bulc.homepage.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class LeadContactImportResult {

    private int totalRows;
    private int registered;
    private int skipped;

    @Singular("error")
    private List<RowError> errors;

    @Getter
    @Builder
    public static class RowError {
        private int rowNumber;
        private String email;
        private String message;
    }
}
