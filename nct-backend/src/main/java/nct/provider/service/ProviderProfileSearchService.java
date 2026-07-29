package nct.provider.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.provider.dto.ProviderCategorySearchRow;
import nct.provider.dto.ProviderCategorySummary;
import nct.provider.dto.ProviderProfileSearchItem;
import nct.provider.mapper.ProviderProfileMapper;
import nct.provider.port.ProviderProfileSearchCriteria;
import nct.provider.port.ProviderProfileSearchReader;

/** 담당자 7 · F-COM-002: 승인된 제공자 프로필 후보를 검색하는 공개 읽기 계약 구현체다. */
@Service
@RequiredArgsConstructor
public class ProviderProfileSearchService implements ProviderProfileSearchReader {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_REGION_LENGTH = 200;
    private static final String DEFAULT_SORT = "rating";

    private final ProviderProfileMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProviderProfileSearchItem> searchApprovedProfiles(ProviderProfileSearchCriteria criteria) {
        SearchParameters parameters = validateAndNormalize(criteria);
        long totalCount = mapper.countApprovedProfiles(
                parameters.keyword(),
                parameters.categorySn(),
                parameters.region());

        if (totalCount == 0 || parameters.offset() >= totalCount) {
            return page(List.of(), totalCount, parameters);
        }

        List<ProviderProfileSearchItem> profiles = mapper.searchApprovedProfiles(
                parameters.keyword(),
                parameters.categorySn(),
                parameters.region(),
                parameters.sort(),
                parameters.offset(),
                parameters.size());
        attachApprovedCategories(profiles);
        return page(profiles, totalCount, parameters);
    }

    private void attachApprovedCategories(List<ProviderProfileSearchItem> profiles) {
        if (profiles.isEmpty()) {
            return;
        }

        List<Long> providerUserSns = profiles.stream()
                .map(ProviderProfileSearchItem::getProviderUserSn)
                .toList();
        Map<Long, List<ProviderCategorySummary>> categoriesByProvider = mapper
                .findApprovedCategoriesByProviderUserSns(providerUserSns)
                .stream()
                .collect(Collectors.groupingBy(
                        ProviderCategorySearchRow::getProviderUserSn,
                        Collectors.mapping(
                                row -> new ProviderCategorySummary(row.getCategorySn(), row.getCategoryName()),
                                Collectors.toList())));

        profiles.forEach(profile -> profile.setCategories(
                categoriesByProvider.getOrDefault(profile.getProviderUserSn(), List.of())));
    }

    private PageResponse<ProviderProfileSearchItem> page(
            List<ProviderProfileSearchItem> content,
            long totalCount,
            SearchParameters parameters) {
        return PageResponse.<ProviderProfileSearchItem>builder()
                .content(content)
                .totalCount(totalCount)
                .page(parameters.page())
                .size(parameters.size())
                .hasNext(((long) parameters.page() + 1) * parameters.size() < totalCount)
                .build();
    }

    private SearchParameters validateAndNormalize(ProviderProfileSearchCriteria criteria) {
        if (criteria == null
                || criteria.page() < 0
                || criteria.size() < 1
                || criteria.size() > MAX_PAGE_SIZE
                || (criteria.categorySn() != null && criteria.categorySn() <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String keyword = normalize(criteria.keyword(), MAX_KEYWORD_LENGTH);
        String region = normalize(criteria.region(), MAX_REGION_LENGTH);
        String sort = normalizeSort(criteria.sort());
        long offset = (long) criteria.page() * criteria.size();
        return new SearchParameters(
                keyword,
                criteria.categorySn(),
                region,
                sort,
                criteria.page(),
                criteria.size(),
                offset);
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return escapeLikePattern(normalized);
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String normalized = sort.trim().toLowerCase();
        if (!"rating".equals(normalized) && !"reviews".equals(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private record SearchParameters(
            String keyword,
            Long categorySn,
            String region,
            String sort,
            int page,
            int size,
            long offset) {
    }
}
