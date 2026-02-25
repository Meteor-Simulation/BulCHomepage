package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.dto.RedeemClaimRequest;
import com.bulc.homepage.licensing.dto.RedeemClaimResponse;
import com.bulc.homepage.licensing.service.RedeemService;
import com.bulc.homepage.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redeem")
@RequiredArgsConstructor
public class RedeemController {

    private final RedeemService redeemService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<RedeemClaimResponse> claim(
            @Valid @RequestBody RedeemClaimRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId();
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        RedeemClaimResponse response = redeemService.claim(userId, request.code(), ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자가 없습니다");
        }
        UUID userId = UUID.fromString(authentication.getName());
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
        return userId;
    }
}
