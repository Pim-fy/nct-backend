package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.provider.dto.ProviderCategorySearchRow;
import nct.provider.dto.ProviderProfileSearchItem;
import nct.provider.mapper.ProviderProfileMapper;
import nct.provider.port.ProviderProfileSearchCriteria;

/** 담당자 7 · F-COM-002: 제공자 검색 조건·페이징·승인 카테고리 조립 회귀 테스트다. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileSearchServiceTest {

    @Mock
    private ProviderProfileMapper mapper;

    @InjectMocks
    private ProviderProfileSearchService service;

    @Test
    void searchesApprovedProfilesAndAttachesCategories() {
        ProviderProfileSearchItem profile = profile(101L);
        ProviderCategorySearchRow category = category(101L, 15L, "청소");
        when(mapper.countApprovedProfiles("입주 청소", 15L, "서울")).thenReturn(1L);
        when(mapper.searchApprovedProfiles("입주 청소", 15L, "서울", "reviews", 0L, 10))
                .thenReturn(List.of(profile));
        when(mapper.findApprovedCategoriesByProviderUserSns(List.of(101L)))
                .thenReturn(List.of(category));

        PageResponse<ProviderProfileSearchItem> result = service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria("  입주 청소  ", 15L, " 서울 ", "REVIEWS", 0, 10));

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(profile);
        assertThat(result.getContent().get(0).getCategories())
                .extracting("categorySn", "categoryName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(15L, "청소"));
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void returnsEmptyPageWithoutRunningListQueries() {
        when(mapper.countApprovedProfiles(null, null, null)).thenReturn(0L);

        PageResponse<ProviderProfileSearchItem> result = service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(null, null, null, null, 0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verify(mapper, never()).searchApprovedProfiles(null, null, null, "rating", 0L, 20);
        verify(mapper, never()).findApprovedCategoriesByProviderUserSns(List.of());
    }

    @Test
    void calculatesZeroBasedOffsetAndHasNext() {
        ProviderProfileSearchItem profile = profile(202L);
        when(mapper.countApprovedProfiles(null, null, null)).thenReturn(35L);
        when(mapper.searchApprovedProfiles(null, null, null, "rating", 20L, 10))
                .thenReturn(List.of(profile));
        when(mapper.findApprovedCategoriesByProviderUserSns(List.of(202L)))
                .thenReturn(List.of());

        PageResponse<ProviderProfileSearchItem> result = service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(null, null, null, "rating", 2, 10));

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.isHasNext()).isTrue();
    }

    @Test
    void largePageNumberDoesNotOverflowHasNextCalculation() {
        when(mapper.countApprovedProfiles(null, null, null)).thenReturn(35L);

        PageResponse<ProviderProfileSearchItem> result = service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(null, null, null, "rating", Integer.MAX_VALUE, 50));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void rejectsUnsupportedCompletedSortUntilTradeContractExists() {
        assertThatThrownBy(() -> service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(null, null, null, "completed", 0, 10)))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void escapesLikeWildcardsAsLiteralSearchText() {
        when(mapper.countApprovedProfiles("100!% 만족!!", null, "서울!_동")).thenReturn(0L);

        service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria("100% 만족!", null, "서울_동", "rating", 0, 10));

        verify(mapper).countApprovedProfiles("100!% 만족!!", null, "서울!_동");
    }

    @Test
    void rejectsInvalidPageSizeAndCategory() {
        assertThatThrownBy(() -> service.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(null, 0L, null, "rating", 0, 51)))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private ProviderProfileSearchItem profile(Long userSn) {
        ProviderProfileSearchItem item = new ProviderProfileSearchItem();
        item.setProviderUserSn(userSn);
        item.setProviderName("제공자");
        item.setReviewAverageScore(new BigDecimal("4.50"));
        item.setReviewCount(12L);
        return item;
    }

    private ProviderCategorySearchRow category(Long userSn, Long categorySn, String categoryName) {
        ProviderCategorySearchRow row = new ProviderCategorySearchRow();
        row.setProviderUserSn(userSn);
        row.setCategorySn(categorySn);
        row.setCategoryName(categoryName);
        return row;
    }
}
