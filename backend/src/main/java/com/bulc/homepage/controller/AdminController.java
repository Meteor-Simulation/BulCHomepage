package com.bulc.homepage.controller;

import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.entity.Product;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.repository.PaymentRepository;
import com.bulc.homepage.repository.PricePlanRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PricePlanRepository pricePlanRepository;
    private final LicenseRepository licenseRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 관리자 권한 체크
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(user -> "000".equals(user.getRolesCode()) || "001".equals(user.getRolesCode()))
                .orElse(false);
    }

    /**
     * 사용자 목록 조회
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<UserResponse> users = userRepository.findAll().stream()
                .map(user -> new UserResponse(
                        user.getEmail(),
                        user.getName(),
                        user.getPhone(),
                        user.getRolesCode(),
                        user.getCountryCode(),
                        user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    /**
     * 상품 목록 조회
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<ProductResponse> products = productRepository.findAll().stream()
                .map(product -> new ProductResponse(
                        product.getCode(),
                        product.getName(),
                        product.getDescription(),
                        product.getCreatedAt() != null ? product.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(products);
    }

    /**
     * 요금제 목록 조회
     */
    @GetMapping("/price-plans")
    public ResponseEntity<List<PricePlanResponse>> getPricePlans() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<PricePlanResponse> plans = pricePlanRepository.findAll().stream()
                .map(plan -> new PricePlanResponse(
                        plan.getId(),
                        plan.getProductCode(),
                        plan.getName(),
                        plan.getDescription(),
                        plan.getPrice().longValue(),
                        plan.getCurrency(),
                        plan.getIsActive(),
                        plan.getCreatedAt() != null ? plan.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(plans);
    }

    /**
     * 라이선스 목록 조회 (전체 목록)
     */
    @GetMapping("/license-list")
    public ResponseEntity<List<LicenseResponse>> getLicenses() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<LicenseResponse> licenses = licenseRepository.findAll().stream()
                .map(license -> new LicenseResponse(
                        license.getId().toString(),
                        license.getLicenseKey(),
                        license.getOwnerType().name(),
                        license.getOwnerId() != null ? license.getOwnerId().toString() : null,
                        license.getStatus().name(),
                        license.getValidUntil() != null ? license.getValidUntil().toString() : null,
                        license.getCreatedAt() != null ? license.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(licenses);
    }

    /**
     * 결제 내역 조회
     */
    @GetMapping("/payments")
    public ResponseEntity<List<PaymentResponse>> getPayments() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<PaymentResponse> payments = paymentRepository.findAll().stream()
                .map(payment -> {
                    String orderId = payment.getPaymentDetail() != null
                            ? payment.getPaymentDetail().getOrderId()
                            : null;
                    String statusText = switch (payment.getStatus()) {
                        case "P" -> "PENDING";
                        case "C" -> "COMPLETED";
                        case "F" -> "FAILED";
                        case "R" -> "REFUNDED";
                        default -> payment.getStatus();
                    };
                    return new PaymentResponse(
                            payment.getId(),
                            payment.getUserEmail(),
                            orderId,
                            payment.getAmount().longValue(),
                            payment.getCurrency(),
                            statusText,
                            payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : null
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(payments);
    }

    // DTOs
    public record UserResponse(String email, String name, String phone, String rolesCode, String countryCode, String createdAt) {}
    public record ProductResponse(String code, String name, String description, String createdAt) {}
    public record PricePlanResponse(Long id, String productCode, String name, String description, Long price, String currency, Boolean isActive, String createdAt) {}
    public record LicenseResponse(String id, String licenseKey, String ownerType, String ownerId, String status, String validUntil, String createdAt) {}
    public record PaymentResponse(Long id, String userEmail, String orderId, Long amount, String currency, String status, String createdAt) {}
}
