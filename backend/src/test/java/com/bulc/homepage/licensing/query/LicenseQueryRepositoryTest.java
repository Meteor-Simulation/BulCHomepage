package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("LicenseQueryRepository 통합 테스트")
class LicenseQueryRepositoryTest {

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private LicenseQueryRepository queryRepository;

    private UUID userId;
    private UUID productId;
    private License activeLicense;
    private License expiredLicense;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        // Active license
        activeLicense = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(userId)
                .productId(productId)
                .planId(UUID.randomUUID())
                .licenseType(LicenseType.SUBSCRIPTION)
                .usageCategory(UsageCategory.COMMERCIAL)
                .validFrom(Instant.now())
                .validUntil(Instant.now().plus(365, ChronoUnit.DAYS))
                .licenseKey("TEST-KEY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .policySnapshot(Map.of(
                        "maxActivations", 3,
                        "maxConcurrentSessions", 2,
                        "gracePeriodDays", 7,
                        "allowOfflineDays", 30,
                        "entitlements", List.of("core", "export")
                ))
                .build();
        activeLicense.activate();
        activeLicense.addActivation("device-1", "1.0.0", "Windows 11", "192.168.1.1");
        licenseRepository.save(activeLicense);

        // Suspended license (expired 상태 대신 suspended 사용)
        expiredLicense = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(userId)
                .productId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .licenseType(LicenseType.TRIAL)
                .usageCategory(UsageCategory.EDUCATION)
                .validFrom(Instant.now().minus(60, ChronoUnit.DAYS))
                .validUntil(Instant.now().minus(30, ChronoUnit.DAYS))
                .policySnapshot(Map.of("maxActivations", 1))
                .build();
        expiredLicense.activate();
        expiredLicense.suspend("Test suspension");
        licenseRepository.save(expiredLicense);
    }

    @Nested
    @DisplayName("findDetailById")
    class FindDetailById {

        @Test
        @DisplayName("ID로 라이선스 상세 조회 - Activation 포함")
        void withActivations() {
            // when
            Optional<LicenseDetailView> result = queryRepository.findDetailById(activeLicense.getId());

            // then
            assertThat(result).isPresent();
            LicenseDetailView view = result.get();
            assertThat(view.id()).isEqualTo(activeLicense.getId());
            assertThat(view.licenseKey()).isEqualTo(activeLicense.getLicenseKey());
            assertThat(view.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(view.activations()).hasSize(1);
            assertThat(view.activations().get(0).deviceFingerprint()).isEqualTo("device-1");
            assertThat(view.policySnapshot().maxActivations()).isEqualTo(3);
            assertThat(view.policySnapshot().entitlements()).containsExactly("core", "export");
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 빈 Optional 반환")
        void notFound() {
            // when
            Optional<LicenseDetailView> result = queryRepository.findDetailById(UUID.randomUUID());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findDetailByKey")
    class FindDetailByKey {

        @Test
        @DisplayName("라이선스 키로 상세 조회")
        void success() {
            // when
            Optional<LicenseDetailView> result = queryRepository.findDetailByKey(activeLicense.getLicenseKey());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(activeLicense.getId());
        }
    }

    @Nested
    @DisplayName("findByOwner")
    class FindByOwner {

        @Test
        @DisplayName("소유자별 라이선스 목록 조회")
        void success() {
            // when
            List<LicenseSummaryView> result = queryRepository.findByOwner(OwnerType.USER, userId);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("라이선스가 없는 소유자 조회 시 빈 목록")
        void emptyList() {
            // when
            List<LicenseSummaryView> result = queryRepository.findByOwner(OwnerType.USER, UUID.randomUUID());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("usedActivations 계산 확인")
        void usedActivationsCount() {
            // when
            List<LicenseSummaryView> result = queryRepository.findByOwner(OwnerType.USER, userId);

            // then
            LicenseSummaryView activeView = result.stream()
                    .filter(v -> v.id().equals(activeLicense.getId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(activeView.usedActivations()).isEqualTo(1);
            assertThat(activeView.maxActivations()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("상태로 검색")
        void byStatus() {
            // given
            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .status(LicenseStatus.ACTIVE)
                    .build();

            // when
            Page<LicenseSummaryView> result = queryRepository.search(cond, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("소유자로 검색")
        void byOwner() {
            // given
            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .build();

            // when
            Page<LicenseSummaryView> result = queryRepository.search(cond, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("라이선스 키로 부분 검색")
        void byLicenseKeyPartial() {
            // given
            String keyPart = activeLicense.getLicenseKey().substring(0, 4);
            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .licenseKey(keyPart)
                    .build();

            // when
            Page<LicenseSummaryView> result = queryRepository.search(cond, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("복합 조건 검색")
        void multipleConditions() {
            // given
            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .status(LicenseStatus.ACTIVE)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .build();

            // when
            Page<LicenseSummaryView> result = queryRepository.search(cond, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).licenseType()).isEqualTo(LicenseType.SUBSCRIPTION);
        }

        @Test
        @DisplayName("페이징 동작 확인")
        void pagination() {
            // given - 추가 라이선스 생성
            for (int i = 0; i < 5; i++) {
                License license = License.builder()
                        .ownerType(OwnerType.USER)
                        .ownerId(userId)
                        .productId(UUID.randomUUID())
                        .planId(UUID.randomUUID())
                        .licenseType(LicenseType.SUBSCRIPTION)
                        .usageCategory(UsageCategory.COMMERCIAL)
                        .validFrom(Instant.now())
                        .validUntil(Instant.now().plus(365, ChronoUnit.DAYS))
                        .policySnapshot(Map.of("maxActivations", 1))
                        .build();
                license.activate();
                licenseRepository.save(license);
            }

            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .build();

            // when
            Page<LicenseSummaryView> page1 = queryRepository.search(cond, PageRequest.of(0, 3));
            Page<LicenseSummaryView> page2 = queryRepository.search(cond, PageRequest.of(1, 3));

            // then
            assertThat(page1.getTotalElements()).isEqualTo(7); // 2 기존 + 5 추가
            assertThat(page1.getContent()).hasSize(3);
            assertThat(page2.getContent()).hasSize(3);
            assertThat(page1.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("조건 없이 검색 시 전체 반환")
        void noCondition() {
            // given
            LicenseSearchCond cond = LicenseSearchCond.builder().build();

            // when
            Page<LicenseSummaryView> result = queryRepository.search(cond, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }
}
