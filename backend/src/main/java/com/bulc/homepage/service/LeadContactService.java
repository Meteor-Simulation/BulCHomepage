package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LeadContactRegisterRequest;
import com.bulc.homepage.dto.request.LeadContactUpdateRequest;
import com.bulc.homepage.dto.response.LeadContactImportResult;
import com.bulc.homepage.entity.LeadContact;
import com.bulc.homepage.repository.LeadContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MDP-549: 메일링 컨택(B2B 영업 수집) 관리 서비스.
 *
 * <p>CSV/Excel 임포트는 한국어 명함 양식 헤더와 영어 헤더 모두 지원.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadContactService {

    private final LeadContactRepository leadContactRepository;

    /** 헤더 별칭 → 내부 정규 필드명 매핑 (소문자·공백제거 키 기준) */
    private static final Map<String, String> HEADER_ALIASES = new HashMap<>();
    static {
        // 한국어 명함 양식 (사용자 양식 12개)
        HEADER_ALIASES.put("회사", "company_name");
        HEADER_ALIASES.put("이름", "contact_name");
        HEADER_ALIASES.put("부서", "department");
        HEADER_ALIASES.put("직함", "role");
        HEADER_ALIASES.put("전자메일주소", "email");
        HEADER_ALIASES.put("이메일", "email");
        HEADER_ALIASES.put("e-mail", "email");
        HEADER_ALIASES.put("근무지주소번지", "address");
        HEADER_ALIASES.put("근무지주소", "address");
        HEADER_ALIASES.put("주소", "address");
        HEADER_ALIASES.put("근무처전화", "work_phone");
        HEADER_ALIASES.put("회사전화", "work_phone");
        HEADER_ALIASES.put("근무처팩스", "work_fax");
        HEADER_ALIASES.put("팩스", "work_fax");
        HEADER_ALIASES.put("휴대폰", "mobile_phone");
        HEADER_ALIASES.put("핸드폰", "mobile_phone");
        HEADER_ALIASES.put("모바일", "mobile_phone");
        HEADER_ALIASES.put("명함등록일", "source_date");
        HEADER_ALIASES.put("명함첩이름", "source_event");
        HEADER_ALIASES.put("메모", "notes");
        // 영어 (backward compat)
        HEADER_ALIASES.put("email", "email");
        HEADER_ALIASES.put("contact_name", "contact_name");
        HEADER_ALIASES.put("contactname", "contact_name");
        HEADER_ALIASES.put("company_name", "company_name");
        HEADER_ALIASES.put("companyname", "company_name");
        HEADER_ALIASES.put("company", "company_name");
        HEADER_ALIASES.put("department", "department");
        HEADER_ALIASES.put("role", "role");
        HEADER_ALIASES.put("title", "role");
        HEADER_ALIASES.put("address", "address");
        HEADER_ALIASES.put("work_phone", "work_phone");
        HEADER_ALIASES.put("workphone", "work_phone");
        HEADER_ALIASES.put("work_fax", "work_fax");
        HEADER_ALIASES.put("workfax", "work_fax");
        HEADER_ALIASES.put("fax", "work_fax");
        HEADER_ALIASES.put("mobile_phone", "mobile_phone");
        HEADER_ALIASES.put("mobilephone", "mobile_phone");
        HEADER_ALIASES.put("mobile", "mobile_phone");
        HEADER_ALIASES.put("source_event", "source_event");
        HEADER_ALIASES.put("source_date", "source_date");
        HEADER_ALIASES.put("collected_by", "collected_by");
        HEADER_ALIASES.put("consent_method", "consent_method");
        HEADER_ALIASES.put("consent_date", "consent_date");
        HEADER_ALIASES.put("opt_in_marketing", "opt_in_marketing");
        HEADER_ALIASES.put("opt_in_transactional", "opt_in_transactional");
        HEADER_ALIASES.put("tags", "tags");
        HEADER_ALIASES.put("notes", "notes");
        HEADER_ALIASES.put("note", "notes");
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                  // 2026-06-04
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy. M. d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    // ---- CRUD --------------------------------------------------------------

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
                .department(req.getDepartment())
                .role(req.getRole())
                .address(req.getAddress())
                .workPhone(req.getWorkPhone())
                .workFax(req.getWorkFax())
                .mobilePhone(req.getMobilePhone())
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
        if (req.getDepartment() != null) c.setDepartment(req.getDepartment());
        if (req.getRole() != null) c.setRole(req.getRole());
        if (req.getAddress() != null) c.setAddress(req.getAddress());
        if (req.getWorkPhone() != null) c.setWorkPhone(req.getWorkPhone());
        if (req.getWorkFax() != null) c.setWorkFax(req.getWorkFax());
        if (req.getMobilePhone() != null) c.setMobilePhone(req.getMobilePhone());
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
                nullToEmpty(emailQ),
                nullToEmpty(nameQ),
                nullToEmpty(companyQ),
                nullToEmpty(tagQ),
                nullToEmpty(sourceEventQ),
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

    // ---- Import (CSV / Excel 자동 감지) ------------------------------------

    /**
     * CSV(.csv) / Excel(.xlsx, .xls) 일괄 임포트.
     *
     * <p>파일명 확장자로 형식 자동 감지. 헤더는 한국어 명함 양식 또는 영어 모두 허용.
     * email 컬럼은 필수. 명함 임포트로 등록된 컨택은 기본값으로
     * {@code consent_method='import'}, {@code opt_in_marketing=false},
     * {@code opt_in_transactional=true} 적용 (정보통신망법 50조 안전 기본값).
     */
    @Transactional
    public LeadContactImportResult importFile(MultipartFile file, UUID adminId) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> rows;
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                rows = readExcel(file);
            } else {
                rows = readCsv(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("파일을 읽는 중 오류: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("빈 파일입니다.");
        }

        List<String> headerRow = rows.get(0);
        Map<String, Integer> colIdx = resolveHeader(headerRow);
        if (!colIdx.containsKey("email")) {
            throw new IllegalArgumentException("헤더에 이메일 컬럼이 필요합니다 (전자 메일 주소 / 이메일 / email).");
        }

        int total = 0;
        int registered = 0;
        int skipped = 0;
        List<LeadContactImportResult.RowError> errors = new ArrayList<>();

        for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
            int rowNumber = rowIdx + 1; // 1-based, header counted
            List<String> cells = rows.get(rowIdx);
            if (cells.stream().allMatch(s -> s == null || s.isBlank())) continue;
            total++;

            String email = getCell(cells, colIdx, "email");
            if (email == null || email.isBlank()) {
                skipped++;
                errors.add(LeadContactImportResult.RowError.builder()
                        .rowNumber(rowNumber).email(email).message("이메일 누락").build());
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
                        .department(getCell(cells, colIdx, "department"))
                        .role(getCell(cells, colIdx, "role"))
                        .address(getCell(cells, colIdx, "address"))
                        .workPhone(getCell(cells, colIdx, "work_phone"))
                        .workFax(getCell(cells, colIdx, "work_fax"))
                        .mobilePhone(getCell(cells, colIdx, "mobile_phone"))
                        .sourceEvent(getCell(cells, colIdx, "source_event"))
                        .sourceDate(parseFlexibleDate(getCell(cells, colIdx, "source_date")))
                        .collectedBy(getCell(cells, colIdx, "collected_by"))
                        .consentMethod(firstNonBlank(getCell(cells, colIdx, "consent_method"), "import"))
                        .consentDate(parseFlexibleDate(getCell(cells, colIdx, "consent_date")))
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

        return LeadContactImportResult.builder()
                .totalRows(total)
                .registered(registered)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    // ---- helpers -----------------------------------------------------------

    private List<List<String>> readCsv(MultipartFile file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private List<List<String>> readExcel(MultipartFile file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row == null) { rows.add(cells); continue; }
                int lastCell = row.getLastCellNum();
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private static Map<String, Integer> resolveHeader(List<String> headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String raw = headerRow.get(i);
            String canonical = canonicalize(raw);
            if (canonical != null && !idx.containsKey(canonical)) {
                idx.put(canonical, i);
            }
        }
        return idx;
    }

    private static String canonicalize(String header) {
        if (header == null) return null;
        String key = header.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return HEADER_ALIASES.get(key);
    }

    private static String getCell(List<String> cells, Map<String, Integer> colIdx, String canonical) {
        Integer i = colIdx.get(canonical);
        if (i == null || i >= cells.size()) return null;
        String v = cells.get(i);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static LocalDate parseFlexibleDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (DateTimeParseException ignored) { /* try next */ }
        }
        // Excel 시리얼 숫자 (예: "45828") 대응
        try {
            double serial = Double.parseDouble(v);
            return org.apache.poi.ss.usermodel.DateUtil.getJavaDate(serial)
                    .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        } catch (NumberFormatException ignored) { /* not a serial */ }
        throw new IllegalArgumentException("날짜 형식 인식 불가: " + s);
    }

    private static boolean parseBool(String s, boolean defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("y") || v.equals("yes") || v.equals("동의");
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** 간단한 CSV 라인 파서. 따옴표 안의 쉼표 처리. */
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
