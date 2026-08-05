package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LeadContactPublicRequest;
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
import java.time.LocalDateTime;
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

    /** 공개 폼(MDP-707) 동의 방식. init.sql 의 consent_method 규약을 따른다. */
    private static final String CONSENT_METHOD_WEB_FORM = "web_form";

    /** 공개 폼 동의 문구 버전. 문구를 개정하면 함께 올려 증빙을 구분한다. */
    private static final String CONSENT_VERSION = "2026-08-05-v2";

    /** 공개 폼에서 고지한 개인정보 이용 목적. */
    private static final String CONSENT_PURPOSE = "free_code_delivery";

    /** 공개 폼은 등록한 관리자가 없으므로 nil UUID 로 "본인 직접 등록"을 표시한다. */
    private static final UUID SELF_REGISTERED_CREATOR = new UUID(0L, 0L);

    private static final String COLLECTED_BY_SELF = "본인 직접 입력(QR)";

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
            DateTimeFormatter.ISO_LOCAL_DATE,                       // 2026-06-04
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy. M. d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            // 한글 표기 (zero-pad / 1자리 모두 허용)
            DateTimeFormatter.ofPattern("yyyy'년' MM'월' dd'일'"),
            DateTimeFormatter.ofPattern("yyyy'년' M'월' d'일'"),
            DateTimeFormatter.ofPattern("yyyy'년'MM'월'dd'일'"),
            DateTimeFormatter.ofPattern("yyyy'년'M'월'd'일'")
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

    /**
     * MDP-707: 전시회 현장 공개 폼(QR)에서 방문자가 직접 등록한다.
     *
     * <p>관리자 {@link #register}와 달리 이미 등록된 이메일이어도 예외를 던지지 않는다.
     * 현장에서 방문자에게 에러를 노출하지 않기 위해 기존 컨택에 이번 입력을 병합하고,
     * 참여 행사 이력을 {@code notes}에 누적한다.
     *
     * <p>이 폼은 무료 배포 코드 발송을 목적으로 개인정보 수집·이용 동의만 받는다.
     * 코드 발송은 본인이 요청한 것을 이행하는 안내성 발송이므로 {@code optInTransactional}
     * 로 충분하며, 광고성 수신 동의({@code optInMarketing})는 여기서 받지도 변경하지도 않는다.
     * 기존 컨택의 수신 동의·해지 상태는 그대로 보존한다.
     *
     * @param clientIp  동의 증빙용 접속 IP
     * @param userAgent 동의 증빙용 User-Agent
     */
    @Transactional
    public LeadContact registerPublic(LeadContactPublicRequest req, String clientIp, String userAgent) {
        String email = normalizeEmail(req.getEmail());
        LocalDate today = LocalDate.now();
        String evidence = buildConsentEvidence(req, clientIp, userAgent);

        Optional<LeadContact> existing = leadContactRepository.findByEmail(email);
        if (existing.isPresent()) {
            LeadContact c = existing.get();
            // 값이 들어온 항목만 갱신한다 (기존 정보를 빈 값으로 덮어쓰지 않음)
            if (hasText(req.getContactName())) c.setContactName(req.getContactName().trim());
            if (hasText(req.getCompanyName())) c.setCompanyName(req.getCompanyName().trim());
            if (hasText(req.getDepartment())) c.setDepartment(req.getDepartment().trim());
            if (hasText(req.getRole())) c.setRole(req.getRole().trim());
            if (hasText(req.getMobilePhone())) c.setMobilePhone(req.getMobilePhone().trim());

            // 행사명은 QR URL 의 ?e= 로 전달된 경우에만 기록한다 (미지정이면 기존 값 유지)
            if (hasText(req.getSourceEvent())) {
                c.setSourceEvent(req.getSourceEvent().trim());
            }
            c.setSourceDate(today);
            c.setConsentMethod(CONSENT_METHOD_WEB_FORM);
            c.setConsentDate(today);
            c.setConsentEvidence(evidence);
            c.setNotes(appendEventHistory(c.getNotes(), req.getSourceEvent(), today));

            // optInMarketing / unsubscribedAt 은 건드리지 않는다.
            // 이 폼에서 광고성 수신 동의를 받지 않으므로 기존 상태를 그대로 보존한다.

            log.info("공개 폼 기존 컨택 병합 - email={}, event={}", email, req.getSourceEvent());
            return leadContactRepository.save(c);
        }

        LeadContact contact = LeadContact.builder()
                .email(email)
                .contactName(trimToNull(req.getContactName()))
                .companyName(trimToNull(req.getCompanyName()))
                .department(trimToNull(req.getDepartment()))
                .role(trimToNull(req.getRole()))
                .mobilePhone(trimToNull(req.getMobilePhone()))
                .sourceEvent(trimToNull(req.getSourceEvent()))
                .sourceDate(today)
                .collectedBy(COLLECTED_BY_SELF)
                .consentMethod(CONSENT_METHOD_WEB_FORM)
                .consentDate(today)
                .consentEvidence(evidence)
                // 광고성 수신 동의는 받지 않았으므로 false. 코드 발송은 안내성으로 처리한다.
                .optInMarketing(false)
                .optInTransactional(true)
                .notes(appendEventHistory(null, req.getSourceEvent(), today))
                .createdBy(SELF_REGISTERED_CREATOR)
                .build();

        log.info("공개 폼 신규 컨택 등록 - email={}, event={}", email, req.getSourceEvent());
        return leadContactRepository.save(contact);
    }

    /** 동의 증빙 문자열. 분쟁 시 "언제·어디서·무엇에 동의했는지" 재현용. */
    private static String buildConsentEvidence(LeadContactPublicRequest req, String clientIp, String userAgent) {
        return String.join(" | ",
                "submittedAt=" + LocalDateTime.now(),
                "event=" + nullToEmpty(req.getSourceEvent()),
                "privacy=agreed",
                "purpose=" + CONSENT_PURPOSE,
                "consentVersion=" + CONSENT_VERSION,
                "ip=" + nullToEmpty(clientIp),
                "ua=" + abbreviate(nullToEmpty(userAgent), 300));
    }

    /** 참여 행사 이력을 notes 에 한 줄씩 누적한다. 행사명이 없으면 날짜만 남긴다. */
    private static String appendEventHistory(String existingNotes, String sourceEvent, LocalDate date) {
        String line = hasText(sourceEvent)
                ? "[" + date + "] " + sourceEvent.trim() + " 현장 등록"
                : "[" + date + "] 현장 등록";
        if (!hasText(existingNotes)) {
            return line;
        }
        if (existingNotes.contains(line)) {
            return existingNotes; // 같은 날 같은 행사 중복 제출은 이력을 늘리지 않음
        }
        return existingNotes + "\n" + line;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
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
                                    String tagQ, String sourceEventQ, String q,
                                    boolean activeOnly, boolean inactiveOnly,
                                    Pageable pageable) {
        return leadContactRepository.search(
                nullToEmpty(emailQ),
                nullToEmpty(nameQ),
                nullToEmpty(companyQ),
                nullToEmpty(tagQ),
                nullToEmpty(sourceEventQ),
                nullToEmpty(q),
                activeOnly,
                inactiveOnly,
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

    /**
     * 관리자 재활성화. unsubscribed_at / unsubscribe_reason 초기화.
     * <p>opt_in_marketing / opt_in_transactional 은 그대로 유지 (별도 동의 갱신은 필요 시 update API 사용).
     */
    @Transactional
    public LeadContact reactivateById(Long id) {
        LeadContact c = findById(id);
        if (!c.isActive()) {
            c.setUnsubscribedAt(null);
            c.setUnsubscribeReason(null);
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
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("파일을 읽는 중 오류: " + e.getMessage(), e);
        }

        List<List<String>> rows;
        try {
            if (isExcelBytes(bytes)) {
                // 확장자가 .csv 여도 실제 내용이 Excel(XLSX/XLS)이면 Excel로 처리
                rows = readExcelBytes(bytes);
            } else {
                rows = readCsvBytes(bytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("파일 파싱 오류: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("빈 파일입니다.");
        }

        List<String> headerRow = rows.get(0);
        Map<String, Integer> colIdx = resolveHeader(headerRow);
        if (!colIdx.containsKey("email")) {
            // 디버그용: 원본 헤더 + canonicalize 결과 함께 노출
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < headerRow.size(); i++) {
                String raw = headerRow.get(i);
                sb.append("[").append(i).append("] '").append(raw == null ? "" : raw)
                  .append("' → canonical='").append(canonicalize(raw)).append("'; ");
            }
            throw new IllegalArgumentException(
                    "헤더에 이메일 컬럼이 필요합니다 (전자 메일 주소 / 이메일 / email). 인식된 헤더: " + sb
            );
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

    /**
     * 파일 내용(magic bytes)이 Excel 인지 검사.
     * XLSX = ZIP(PK\x03\x04), XLS = CFB/OLE2(D0CF11E0A1B11AE1)
     */
    private static boolean isExcelBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // XLSX/ZIP signature
        if (bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 0x03 && bytes[3] == 0x04) return true;
        // XLS / CFB signature
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0
                && (bytes[4] & 0xFF) == 0xA1 && (bytes[5] & 0xFF) == 0xB1
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0xE1) return true;
        return false;
    }

    private List<List<String>> readCsvBytes(byte[] bytes) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private List<List<String>> readExcelBytes(byte[] bytes) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = new java.io.ByteArrayInputStream(bytes);
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
        String key = header
                .replace("﻿", "")          // UTF-8 BOM
                .replace(" ", "")          // non-breaking space (NBSP)
                .replace("​", "")          // zero-width space
                .replace("　", "")          // CJK 전각 공백
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Z}]+", ""); // 일반·Unicode 공백 일괄 제거
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
        // 다중 공백 단일화, 양끝 공백 제거
        String v = s.trim().replaceAll("\\s+", " ");
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
