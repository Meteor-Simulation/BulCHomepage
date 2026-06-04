package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.LeadContactRegisterRequest;
import com.bulc.homepage.dto.request.LeadContactUnsubscribeRequest;
import com.bulc.homepage.dto.request.LeadContactUpdateRequest;
import com.bulc.homepage.dto.response.LeadContactImportResult;
import com.bulc.homepage.dto.response.LeadContactResponse;
import com.bulc.homepage.entity.LeadContact;
import com.bulc.homepage.service.LeadContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * MDP-549: 관리자용 메일링 컨택 관리 API.
 *
 * 전시회·세미나 등에서 수집한 외부 컨택을 등록·수정·해지·CSV 임포트한다.
 */
@RestController
@RequestMapping("/api/v1/admin/lead-contacts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LeadContactAdminController {

    private final LeadContactService leadContactService;

    /** 단건 등록 */
    @PostMapping
    public ResponseEntity<LeadContactResponse> register(
            @Valid @RequestBody LeadContactRegisterRequest req,
            Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        LeadContact saved = leadContactService.register(req, adminId);
        return ResponseEntity.ok(LeadContactResponse.from(saved));
    }

    /** 검색 + 페이징 */
    @GetMapping
    public ResponseEntity<Page<LeadContactResponse>> search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sourceEvent,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LeadContact> page = leadContactService.search(email, name, company, tag, sourceEvent, activeOnly, pageable);
        return ResponseEntity.ok(page.map(LeadContactResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadContactResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(LeadContactResponse.from(leadContactService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeadContactResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LeadContactUpdateRequest req) {
        return ResponseEntity.ok(LeadContactResponse.from(leadContactService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        leadContactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 관리자가 강제 해지 (수신자 측 1-Click 해지와 별도) */
    @PostMapping("/{id}/unsubscribe")
    public ResponseEntity<LeadContactResponse> unsubscribe(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeadContactUnsubscribeRequest req) {
        String reason = req == null ? null : req.getReason();
        return ResponseEntity.ok(LeadContactResponse.from(leadContactService.unsubscribeById(id, reason)));
    }

    /**
     * CSV 일괄 임포트.
     * <p>multipart/form-data, field name: file
     * <p>헤더 필수, email 컬럼 필수. 컬럼: email, contact_name, company_name, role,
     * source_event, source_date(yyyy-MM-dd), collected_by, consent_method, consent_date(yyyy-MM-dd),
     * opt_in_marketing(true/false/1/0), opt_in_transactional, tags, notes
     */
    @PostMapping(value = "/import/csv", consumes = "multipart/form-data")
    public ResponseEntity<LeadContactImportResult> importCsv(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID adminId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(leadContactService.importCsv(file, adminId));
    }
}
