package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LeadContactRegisterRequest;
import com.bulc.homepage.dto.request.LeadContactUpdateRequest;
import com.bulc.homepage.dto.response.LeadContactImportResult;
import com.bulc.homepage.entity.LeadContact;
import com.bulc.homepage.repository.LeadContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MDP-549: 메일링 컨택(B2B 영업 수집) 관리 서비스.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadContactService {

    private final LeadContactRepository leadContactRepository;

    private static final List<String> CSV_COLUMNS = List.of(
            "email", "contact_name", "company_name", "role",
            "source_event", "source_date", "collected_by",
            "consent_method", "consent_date",
            "opt_in_marketing", "opt_in_transactional",
            "tags", "notes"
    );

    @Transactional
    public LeadContact register(LeadContactRegisterRequest req, UUID adminId) {
        String email = normalizeEmail(req.getEmail());
        if (leadContactRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 등록된 이메일입니다: " + email);
        }

        LeadContact contact = LeadContact.builder()
                .email(email)
                .contactName(req.getContactName())
                .companyName(req.getCompanyName())
                .role(req.getRole())
                .sourceEvent(req.getSourceEvent())
                .sourceDate(req.getSourceDate())
                .collectedBy(req.getCollectedBy())
                .consentMethod(req.getConsentMethod())
                .consentDate(req.getConsentDate())
                .consentEvidence(req.getConsentEvidence())
                .optInMarketing(Boolean.TRUE.equals(req.getOptInMarketing()))
                .optInTransactional(req.getOptInTransactional() == null ? true : req.getOptInTransactional())
                .tags(req.getTags())
                .notes(req.getNotes())
                .createdBy(adminId)
                .build();

        return leadContactRepository.save(contact);
    }

    @Transactional
    public LeadContact update(Long id, LeadContactUpdateRequest req) {
        LeadContact c = leadContactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("컨택을 찾을 수 없습니다: id=" + id));

        if (req.getEmail() != null) {
            String newEmail = normalizeEmail(req.getEmail());
            if (!newEmail.equalsIgnoreCase(c.getEmail()) && leadContactRepository.existsByEmail(newEmail)) {
                throw new IllegalStateException("이미 등록된 이메일입니다: " + newEmail);
            }
            c.setEmail(newEmail);
        }
        if (req.getContactName() != null) c.setContactName(req.getContactName());
        if (req.getCompanyName() != null) c.setCompanyName(req.getCompanyName());
        if (req.getRole() != null) c.setRole(req.getRole());
        if (req.getSourceEvent() != null) c.setSourceEvent(req.getSourceEvent());
        if (req.getSourceDate() != null) c.setSourceDate(req.getSourceDate());
        if (req.getCollectedBy() != null) c.setCollectedBy(req.getCollectedBy());
        if (req.getConsentMethod() != null) c.setConsentMethod(req.getConsentMethod());
        if (req.getConsentDate() != null) c.setConsentDate(req.getConsentDate());
        if (req.getConsentEvidence() != null) c.setConsentEvidence(req.getConsentEvidence());
        if (req.getOptInMarketing() != null) c.setOptInMarketing(req.getOptInMarketing());
        if (req.getOptInTransactional() != null) c.setOptInTransactional(req.getOptInTransactional());
        if (req.getTags() != null) c.setTags(req.getTags());
        if (req.getNotes() != null) c.setNotes(req.getNotes());

        return leadContactRepository.save(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!leadContactRepository.existsById(id)) {
            throw new IllegalArgumentException("컨택을 찾을 수 없습니다: id=" + id);
        }
        leadContactRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public LeadContact findById(Long id) {
        return leadContactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("컨택을 찾을 수 없습니다: id=" + id));
    }

    @Transactional(readOnly = true)
    public Page<LeadContact> search(String emailQ, String nameQ, String companyQ,
                                    String tagQ, String sourceEventQ, boolean activeOnly,
                                    Pageable pageable) {
        return leadContactRepository.search(
                blankToNull(emailQ),
                blankToNull(nameQ),
                blankToNull(companyQ),
                blankToNull(tagQ),
                blankToNull(sourceEventQ),
                activeOnly,
                pageable
        );
    }

    @Transactional
    public LeadContact unsubscribeById(Long id, String reason) {
        LeadContact c = findById(id);
        if (c.isActive()) {
            c.markUnsubscribed(reason);
            leadContactRepository.save(c);
        }
        return c;
    }

    @Transactional
    public Optional<LeadContact> unsubscribeByToken(UUID token, String reason) {
        return leadContactRepository.findByUnsubscribeToken(token)
                .map(c -> {
                    if (c.isActive()) {
                        c.markUnsubscribed(reason);
                        leadContactRepository.save(c);
                    }
                    return c;
                });
    }

    /**
     * CSV 일괄 등록. 첫 줄은 헤더(필수). 컬럼은 CSV_COLUMNS 참고.
     * <p>날짜 형식: yyyy-MM-dd / 불리언: true|false|1|0
     */
    @Transactional
    public LeadContactImportResult importCsv(MultipartFile file, UUID adminId) {
        LeadContactImportResult.LeadContactImportResultBuilder result = LeadContactImportResult.builder();
        int total = 0;
        int registered = 0;
        int skipped = 0;
        List<LeadContactImportResult.RowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("빈 파일입니다.");
            }
            Map<String, Integer> colIdx = parseHeader(header);
            if (!colIdx.containsKey("email")) {
                throw new IllegalArgumentException("CSV 헤더에 'email' 컬럼이 필요합니다.");
            }

            String line;
            int rowNumber = 1; // header is row 1
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                total++;
                List<String> cells = parseCsvLine(line);
                String email = getCell(cells, colIdx, "email");
                if (email == null || email.isBlank()) {
                    skipped++;
                    errors.add(LeadContactImportResult.RowError.builder()
                            .rowNumber(rowNumber).email(email).message("email 누락").build());
                    continue;
                }
                String normalized = normalizeEmail(email);
                if (leadContactRepository.existsByEmail(normalized)) {
                    skipped++;
                    errors.add(LeadContactImportResult.RowError.builder()
                            .rowNumber(rowNumber).email(normalized).message("이미 등록된 이메일").build());
                    continue;
                }
                try {
                    LeadContact contact = LeadContact.builder()
                            .email(normalized)
                            .contactName(getCell(cells, colIdx, "contact_name"))
                            .companyName(getCell(cells, colIdx, "company_name"))
                            .role(getCell(cells, colIdx, "role"))
                            .sourceEvent(getCell(cells, colIdx, "source_event"))
                            .sourceDate(parseDate(getCell(cells, colIdx, "source_date")))
                            .collectedBy(getCell(cells, colIdx, "collected_by"))
                            .consentMethod(getCell(cells, colIdx, "consent_method"))
                            .consentDate(parseDate(getCell(cells, colIdx, "consent_date")))
                            .optInMarketing(parseBool(getCell(cells, colIdx, "opt_in_marketing"), false))
                            .optInTransactional(parseBool(getCell(cells, colIdx, "opt_in_transactional"), true))
                            .tags(getCell(cells, colIdx, "tags"))
                            .notes(getCell(cells, colIdx, "notes"))
                            .createdBy(adminId)
                            .build();
                    leadContactRepository.save(contact);
                    registered++;
                } catch (Exception ex) {
                    skipped++;
                    errors.add(LeadContactImportResult.RowError.builder()
                            .rowNumber(rowNumber).email(normalized).message(ex.getMessage()).build());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV 파일을 읽는 중 오류: " + e.getMessage(), e);
        }

        return result
                .totalRows(total)
                .registered(registered)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    // ---- helpers -----------------------------------------------------------

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Map<String, Integer> parseHeader(String header) {
        Map<String, Integer> idx = new HashMap<>();
        List<String> cols = parseCsvLine(header);
        for (int i = 0; i < cols.size(); i++) {
            idx.put(cols.get(i).trim().toLowerCase(), i);
        }
        return idx;
    }

    private static String getCell(List<String> cells, Map<String, Integer> colIdx, String column) {
        Integer i = colIdx.get(column);
        if (i == null || i >= cells.size()) return null;
        String v = cells.get(i);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("날짜 형식 오류 (yyyy-MM-dd 필요): " + s);
        }
    }

    private static boolean parseBool(String s, boolean defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("y") || v.equals("yes");
    }

    /**
     * 간단한 CSV 라인 파서. 따옴표 안의 쉼표 처리.
     */
    static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        result.add(cur.toString());
        return result;
    }
}
