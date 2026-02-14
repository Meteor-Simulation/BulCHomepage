package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.RedeemCampaignStatus;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.service.RedeemAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/redeem-campaigns")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RedeemAdminController {

    private final RedeemAdminService service;

    @GetMapping
    public Page<RedeemCampaignResponse> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) RedeemCampaignStatus status) {
        return service.listCampaigns(pageable, status);
    }

    @GetMapping("/{id}")
    public RedeemCampaignResponse get(@PathVariable UUID id) {
        return service.getCampaign(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RedeemCampaignResponse create(@Valid @RequestBody RedeemCampaignRequest request) {
        UUID adminId = getCurrentUserId();
        return service.createCampaign(request, adminId);
    }

    @PutMapping("/{id}")
    public RedeemCampaignResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody RedeemCampaignRequest request) {
        return service.updateCampaign(id, request);
    }

    @PatchMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable UUID id) {
        service.pauseCampaign(id);
    }

    @PatchMapping("/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@PathVariable UUID id) {
        service.endCampaign(id);
    }

    @PatchMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable UUID id) {
        service.resumeCampaign(id);
    }

    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public RedeemCodeGenerateResponse generateCodes(@Valid @RequestBody RedeemCodeGenerateRequest request) {
        return service.generateCodes(request);
    }

    @GetMapping("/{campaignId}/codes")
    public Page<RedeemCodeResponse> listCodes(
            @PathVariable UUID campaignId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listCodes(campaignId, pageable);
    }

    @DeleteMapping("/codes/{codeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCode(@PathVariable UUID codeId) {
        service.deactivateCode(codeId);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자가 없습니다");
        }
        return UUID.fromString(authentication.getName());
    }
}
