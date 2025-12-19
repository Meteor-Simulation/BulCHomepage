package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("LicenseQueryService 테스트")
class LicenseQueryServiceTest {

    @Mock
    private LicenseQueryRepository queryRepository;

    @InjectMocks
    private LicenseQueryServiceImpl queryService;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("존재하는 라이선스 조회 성공")
        void success() {
            // given
            UUID licenseId = UUID.randomUUID();
            LicenseDetailView mockView = mock(LicenseDetailView.class);
            given(queryRepository.findDetailById(licenseId)).willReturn(Optional.of(mockView));

            // when
            LicenseDetailView result = queryService.getById(licenseId);

            // then
            assertThat(result).isEqualTo(mockView);
        }

        @Test
        @DisplayName("존재하지 않는 라이선스 조회 시 예외")
        void notFound() {
            // given
            UUID licenseId = UUID.randomUUID();
            given(queryRepository.findDetailById(licenseId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> queryService.getById(licenseId))
                    .isInstanceOf(LicenseException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            LicenseException.ErrorCode.LICENSE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getByKey")
    class GetByKey {

        @Test
        @DisplayName("존재하는 라이선스 키로 조회 성공")
        void success() {
            // given
            String licenseKey = "ABCD-1234-EFGH-5678";
            LicenseDetailView mockView = mock(LicenseDetailView.class);
            given(queryRepository.findDetailByKey(licenseKey)).willReturn(Optional.of(mockView));

            // when
            LicenseDetailView result = queryService.getByKey(licenseKey);

            // then
            assertThat(result).isEqualTo(mockView);
        }

        @Test
        @DisplayName("존재하지 않는 라이선스 키로 조회 시 예외")
        void notFound() {
            // given
            String licenseKey = "INVALID-KEY";
            given(queryRepository.findDetailByKey(licenseKey)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> queryService.getByKey(licenseKey))
                    .isInstanceOf(LicenseException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            LicenseException.ErrorCode.LICENSE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("findByOwner")
    class FindByOwner {

        @Test
        @DisplayName("소유자별 라이선스 목록 조회")
        void success() {
            // given
            OwnerType ownerType = OwnerType.USER;
            UUID ownerId = UUID.randomUUID();
            List<LicenseSummaryView> mockList = List.of(
                    mock(LicenseSummaryView.class),
                    mock(LicenseSummaryView.class)
            );
            given(queryRepository.findByOwner(ownerType, ownerId)).willReturn(mockList);

            // when
            List<LicenseSummaryView> result = queryService.findByOwner(ownerType, ownerId);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("라이선스가 없는 소유자 조회 시 빈 목록 반환")
        void emptyList() {
            // given
            OwnerType ownerType = OwnerType.USER;
            UUID ownerId = UUID.randomUUID();
            given(queryRepository.findByOwner(ownerType, ownerId)).willReturn(List.of());

            // when
            List<LicenseSummaryView> result = queryService.findByOwner(ownerType, ownerId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("검색 조건으로 라이선스 검색")
        void success() {
            // given
            LicenseSearchCond cond = LicenseSearchCond.builder()
                    .ownerType(OwnerType.USER)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);
            Page<LicenseSummaryView> mockPage = new PageImpl<>(
                    List.of(mock(LicenseSummaryView.class)),
                    pageable,
                    1
            );
            given(queryRepository.search(eq(cond), any(Pageable.class))).willReturn(mockPage);

            // when
            Page<LicenseSummaryView> result = queryService.search(cond, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }
    }
}
