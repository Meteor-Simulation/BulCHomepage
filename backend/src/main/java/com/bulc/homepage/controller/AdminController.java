package com.bulc.homepage.controller;

import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.entity.Product;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.LicenseStatus;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
     * 관리자 권한 체크 (000 또는 001)
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
     * 시스템 관리자 권한 체크 (000만)
     */
    private boolean isSystemAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(user -> "000".equals(user.getRolesCode()))
                .orElse(false);
    }

    /**
     * 현재 로그인한 사용자의 권한 코드 조회
     */
    private String getCurrentUserRolesCode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(User::getRolesCode)
                .orElse(null);
    }

    /**
     * 사용자 목록 조회 (활성 사용자만)
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<UserResponse> users = userRepository.findAll().stream()
                .filter(user -> user.getIsActive() != null && user.getIsActive()) // 비활성화된 계정 필터링
                .sorted((u1, u2) -> {
                    // 1차: 권한순 (000 > 001 > 002, 오름차순 정렬)
                    int roleCompare = u1.getRolesCode().compareTo(u2.getRolesCode());
                    if (roleCompare != 0) return roleCompare;
                    // 2차: 가입일순 (먼저 가입한 순서, 오름차순 정렬)
                    if (u1.getCreatedAt() == null && u2.getCreatedAt() == null) return 0;
                    if (u1.getCreatedAt() == null) return 1;
                    if (u2.getCreatedAt() == null) return -1;
                    return u1.getCreatedAt().compareTo(u2.getCreatedAt());
                })
                .map(user -> new UserResponse(
                        user.getEmail(),  // email이 PK
                        user.getEmail(),
                        user.getName(),
                        user.getPhone(),
                        user.getRolesCode(),
                        user.getCountryCode(),
                        user.getIsActive(),
                        user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    /**
     * 사용자 권한 수정
     * - 관리자(000): 모든 권한 수정 가능
     * - 매니저(001): 관리자(000) 권한 수정 불가, 관리자(000)로 수정 불가
     * userId는 email (User의 PK)
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String userId,
            @RequestBody RoleUpdateRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new ErrorResponse("관리자 권한이 필요합니다."));
        }

        // 유효한 권한 코드 체크
        if (!List.of("000", "001", "002").contains(request.rolesCode())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("유효하지 않은 권한 코드입니다."));
        }

        String currentUserRole = getCurrentUserRolesCode();

        // 매니저(001)인 경우 제한 적용
        if ("001".equals(currentUserRole)) {
            // 관리자(000)로 수정 불가
            if ("000".equals(request.rolesCode())) {
                return ResponseEntity.status(403)
                        .body(new ErrorResponse("매니저는 관리자 권한을 부여할 수 없습니다."));
            }
        }

        // userId는 email (User의 PK)
        return userRepository.findById(userId)
                .map(user -> {
                    // 매니저(001)인 경우: 관리자(000) 계정 수정 불가
                    if ("001".equals(currentUserRole) && "000".equals(user.getRolesCode())) {
                        return ResponseEntity.status(403)
                                .body(new ErrorResponse("매니저는 관리자 계정의 권한을 수정할 수 없습니다."));
                    }

                    user.setRolesCode(request.rolesCode());
                    userRepository.save(user);
                    return ResponseEntity.ok(new UserResponse(
                            user.getEmail(),  // email이 PK
                            user.getEmail(),
                            user.getName(),
                            user.getPhone(),
                            user.getRolesCode(),
                            user.getCountryCode(),
                            user.getIsActive(),
                            user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
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
                        product.getIsActive(),
                        product.getCreatedAt() != null ? product.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(products);
    }

    /**
     * 상품 생성
     */
    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        if (request.code() == null || request.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "상품 코드는 필수입니다."));
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "상품명은 필수입니다."));
        }
        if (productRepository.existsById(request.code())) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 상품 코드입니다."));
        }

        Product product = Product.builder()
                .code(request.code().toUpperCase())
                .name(request.name())
                .description(request.description())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(new ProductResponse(
                saved.getCode(),
                saved.getName(),
                saved.getDescription(),
                saved.getIsActive(),
                saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
        ));
    }

    /**
     * 상품 수정
     */
    @PutMapping("/products/{code}")
    public ResponseEntity<?> updateProduct(@PathVariable String code, @RequestBody ProductRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return productRepository.findById(code)
                .map(product -> {
                    if (request.name() != null && !request.name().isBlank()) {
                        product.setName(request.name());
                    }
                    if (request.description() != null) {
                        product.setDescription(request.description());
                    }
                    if (request.isActive() != null) {
                        product.setIsActive(request.isActive());
                    }
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok(new ProductResponse(
                            saved.getCode(),
                            saved.getName(),
                            saved.getDescription(),
                            saved.getIsActive(),
                            saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 상품 삭제 (비활성화)
     */
    @DeleteMapping("/products/{code}")
    public ResponseEntity<?> deleteProduct(@PathVariable String code) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return productRepository.findById(code)
                .map(product -> {
                    product.setIsActive(false);
                    productRepository.save(product);
                    return ResponseEntity.ok(Map.of("message", "상품이 비활성화되었습니다."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 상품 활성화/비활성화 토글
     */
    @PatchMapping("/products/{code}/toggle")
    public ResponseEntity<?> toggleProduct(@PathVariable String code) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return productRepository.findById(code)
                .map(product -> {
                    product.setIsActive(!product.getIsActive());
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok(new ProductResponse(
                            saved.getCode(),
                            saved.getName(),
                            saved.getDescription(),
                            saved.getIsActive(),
                            saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
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
                        plan.getLicensePlanId() != null ? plan.getLicensePlanId().toString() : null,
                        plan.getCreatedAt() != null ? plan.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(plans);
    }

    /**
     * 요금제 생성
     */
    @PostMapping("/price-plans")
    public ResponseEntity<?> createPricePlan(@RequestBody PricePlanRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        if (request.productCode() == null || request.productCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "상품 코드는 필수입니다."));
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "요금제명은 필수입니다."));
        }
        if (request.price() == null || request.price() < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "가격은 0 이상이어야 합니다."));
        }
        if (!productRepository.existsById(request.productCode())) {
            return ResponseEntity.badRequest().body(Map.of("message", "존재하지 않는 상품 코드입니다."));
        }

        PricePlan plan = PricePlan.builder()
                .productCode(request.productCode())
                .name(request.name())
                .description(request.description())
                .price(BigDecimal.valueOf(request.price()))
                .currency(request.currency() != null ? request.currency() : "KRW")
                .isActive(request.isActive() != null ? request.isActive() : true)
                .licensePlanId(request.licensePlanId() != null ? java.util.UUID.fromString(request.licensePlanId()) : null)
                .build();

        PricePlan saved = pricePlanRepository.save(plan);
        return ResponseEntity.ok(new PricePlanResponse(
                saved.getId(),
                saved.getProductCode(),
                saved.getName(),
                saved.getDescription(),
                saved.getPrice().longValue(),
                saved.getCurrency(),
                saved.getIsActive(),
                saved.getLicensePlanId() != null ? saved.getLicensePlanId().toString() : null,
                saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
        ));
    }

    /**
     * 요금제 수정
     */
    @PutMapping("/price-plans/{id}")
    public ResponseEntity<?> updatePricePlan(@PathVariable Long id, @RequestBody PricePlanRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return pricePlanRepository.findById(id)
                .map(plan -> {
                    if (request.name() != null && !request.name().isBlank()) {
                        plan.setName(request.name());
                    }
                    if (request.description() != null) {
                        plan.setDescription(request.description());
                    }
                    if (request.price() != null) {
                        plan.setPrice(BigDecimal.valueOf(request.price()));
                    }
                    if (request.currency() != null) {
                        plan.setCurrency(request.currency());
                    }
                    if (request.isActive() != null) {
                        plan.setIsActive(request.isActive());
                    }
                    if (request.licensePlanId() != null) {
                        plan.setLicensePlanId(java.util.UUID.fromString(request.licensePlanId()));
                    }
                    PricePlan saved = pricePlanRepository.save(plan);
                    return ResponseEntity.ok(new PricePlanResponse(
                            saved.getId(),
                            saved.getProductCode(),
                            saved.getName(),
                            saved.getDescription(),
                            saved.getPrice().longValue(),
                            saved.getCurrency(),
                            saved.getIsActive(),
                            saved.getLicensePlanId() != null ? saved.getLicensePlanId().toString() : null,
                            saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 요금제 삭제 (비활성화)
     */
    @DeleteMapping("/price-plans/{id}")
    public ResponseEntity<?> deletePricePlan(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return pricePlanRepository.findById(id)
                .map(plan -> {
                    plan.setIsActive(false);
                    pricePlanRepository.save(plan);
                    return ResponseEntity.ok(Map.of("message", "요금제가 비활성화되었습니다."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 요금제 활성화/비활성화 토글
     */
    @PatchMapping("/price-plans/{id}/toggle")
    public ResponseEntity<?> togglePricePlan(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return pricePlanRepository.findById(id)
                .map(plan -> {
                    plan.setIsActive(!plan.getIsActive());
                    PricePlan saved = pricePlanRepository.save(plan);
                    return ResponseEntity.ok(new PricePlanResponse(
                            saved.getId(),
                            saved.getProductCode(),
                            saved.getName(),
                            saved.getDescription(),
                            saved.getPrice().longValue(),
                            saved.getCurrency(),
                            saved.getIsActive(),
                            saved.getLicensePlanId() != null ? saved.getLicensePlanId().toString() : null,
                            saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 라이선스 목록 조회 (전체 목록)
     */
    @GetMapping("/license-list")
    public ResponseEntity<List<LicenseResponse>> getLicenses() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        // 사용자 이메일 -> UUID 매핑 생성
        java.util.Map<java.util.UUID, String> ownerIdToEmail = new java.util.HashMap<>();
        userRepository.findAll().forEach(user -> {
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
                    user.getEmail().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ownerIdToEmail.put(uuid, user.getEmail());
        });

        List<LicenseResponse> licenses = licenseRepository.findAll().stream()
                .map(license -> {
                    // owner_id를 이메일로 변환
                    String ownerDisplay = license.getOwnerId() != null
                            ? ownerIdToEmail.getOrDefault(license.getOwnerId(), license.getOwnerId().toString())
                            : null;
                    return new LicenseResponse(
                            license.getId().toString(),
                            license.getLicenseKey(),
                            license.getOwnerType().name(),
                            ownerDisplay,
                            license.getStatus().name(),
                            license.getValidUntil() != null ? license.getValidUntil().toString() : null,
                            license.getCreatedAt() != null ? license.getCreatedAt().toString() : null
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(licenses);
    }

    /**
     * 라이선스 활성화 (SUSPENDED -> ACTIVE)
     */
    @PatchMapping("/licenses/{id}/activate")
    public ResponseEntity<?> activateLicense(@PathVariable String id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            java.util.UUID licenseId = java.util.UUID.fromString(id);
            return licenseRepository.findById(licenseId)
                    .map(license -> {
                        try {
                            license.unsuspend();
                            licenseRepository.save(license);
                            return ResponseEntity.ok(Map.of("message", "라이선스가 활성화되었습니다."));
                        } catch (IllegalStateException e) {
                            return ResponseEntity.badRequest()
                                    .body(Map.of("message", e.getMessage()));
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 라이선스 ID입니다."));
        }
    }

    /**
     * 라이선스 비활성화 (ACTIVE -> SUSPENDED)
     */
    @PatchMapping("/licenses/{id}/suspend")
    public ResponseEntity<?> suspendLicense(@PathVariable String id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            java.util.UUID licenseId = java.util.UUID.fromString(id);
            return licenseRepository.findById(licenseId)
                    .map(license -> {
                        try {
                            license.suspend("관리자에 의한 비활성화");
                            licenseRepository.save(license);
                            return ResponseEntity.ok(Map.of("message", "라이선스가 비활성화되었습니다."));
                        } catch (IllegalStateException e) {
                            return ResponseEntity.badRequest()
                                    .body(Map.of("message", e.getMessage()));
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 라이선스 ID입니다."));
        }
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
                    String paymentMethod = payment.getPaymentDetail() != null
                            ? payment.getPaymentDetail().getPaymentMethod()
                            : null;
                    String statusText = switch (payment.getStatus()) {
                        case "P" -> "PENDING";
                        case "C" -> "COMPLETED";
                        case "F" -> "FAILED";
                        case "R" -> "REFUNDED";
                        default -> payment.getStatus();
                    };

                    // 사용자 정보 조회 (우선순위: User 관계 > userEmailFk > userName 필드)
                    // 비활성화된 계정은 "탈퇴한 사용자"로 표시
                    String userName = null;
                    String userEmail = null;
                    boolean isDeactivated = false;

                    // 1. User 관계가 있으면 사용
                    if (payment.getUser() != null) {
                        if (payment.getUser().getIsActive() != null && !payment.getUser().getIsActive()) {
                            isDeactivated = true;
                        } else {
                            userName = payment.getUser().getName();
                            userEmail = payment.getUser().getEmail();
                        }
                    }
                    // 2. userEmailFk로 조회
                    else if (payment.getUserEmailFk() != null && !payment.getUserEmailFk().isBlank()) {
                        var userOpt = userRepository.findByEmail(payment.getUserEmailFk());
                        if (userOpt.isPresent()) {
                            var user = userOpt.get();
                            if (user.getIsActive() != null && !user.getIsActive()) {
                                isDeactivated = true;
                            } else {
                                userName = user.getName();
                                userEmail = user.getEmail();
                            }
                        }
                    }

                    // 비활성화된 사용자 처리
                    if (isDeactivated) {
                        userName = "탈퇴한 사용자";
                        userEmail = "(탈퇴)";
                    }
                    // 3. Payment에 저장된 값 사용 (비활성화가 아닌 경우만)
                    else {
                        if (userName == null) {
                            userName = payment.getUserName();
                        }
                        if (userEmail == null) {
                            userEmail = payment.getUserEmailFk() != null ? payment.getUserEmailFk() : payment.getUserEmail();
                        }
                    }

                    // 카드 결제 정보
                    String cardCompany = null;
                    String cardNumber = null;
                    Integer installmentMonths = null;
                    String approveNo = null;

                    // 간편결제 정보
                    String easyPayProvider = null;

                    // 가상계좌/계좌이체 추가 정보
                    String bankName = null;
                    String accountNumber = null;
                    String dueDate = null;
                    String depositorName = null;
                    String settlementStatus = null;

                    if (payment.getPaymentDetail() != null) {
                        var detail = payment.getPaymentDetail();
                        // 카드 정보
                        cardCompany = detail.getCardCompany();
                        cardNumber = detail.getCardNumber();
                        installmentMonths = detail.getInstallmentMonths();
                        approveNo = detail.getApproveNo();
                        // 간편결제 정보
                        easyPayProvider = detail.getEasyPayProvider();
                        // 가상계좌/계좌이체 정보
                        bankName = detail.getBankName();
                        accountNumber = detail.getAccountNumber();
                        dueDate = detail.getDueDate() != null ? detail.getDueDate().toString() : null;
                        depositorName = detail.getDepositorName();
                        settlementStatus = detail.getSettlementStatus();
                    }

                    return new PaymentResponse(
                            payment.getId(),
                            userEmail,
                            userName,
                            orderId,
                            payment.getAmount().longValue(),
                            payment.getCurrency(),
                            statusText,
                            paymentMethod,
                            cardCompany,
                            cardNumber,
                            installmentMonths,
                            approveNo,
                            easyPayProvider,
                            bankName,
                            accountNumber,
                            dueDate,
                            depositorName,
                            settlementStatus,
                            payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : null
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(payments);
    }

    // DTOs
    public record UserResponse(String id, String email, String name, String phone, String rolesCode, String countryCode, Boolean isActive, String createdAt) {}
    public record ProductResponse(String code, String name, String description, Boolean isActive, String createdAt) {}
    public record ProductRequest(String code, String name, String description, Boolean isActive) {}
    public record PricePlanResponse(Long id, String productCode, String name, String description, Long price, String currency, Boolean isActive, String licensePlanId, String createdAt) {}
    public record PricePlanRequest(String productCode, String name, String description, Long price, String currency, Boolean isActive, String licensePlanId) {}
    public record LicenseResponse(String id, String licenseKey, String ownerType, String ownerId, String status, String validUntil, String createdAt) {}
    public record PaymentResponse(Long id, String userEmail, String userName, String orderId, Long amount, String currency, String status, String paymentMethod, String cardCompany, String cardNumber, Integer installmentMonths, String approveNo, String easyPayProvider, String bankName, String accountNumber, String dueDate, String depositorName, String settlementStatus, String createdAt) {}
    public record RoleUpdateRequest(String rolesCode) {}
    public record ErrorResponse(String message) {}
}
