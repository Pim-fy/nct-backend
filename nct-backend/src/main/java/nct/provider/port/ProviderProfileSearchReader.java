package nct.provider.port;

import nct.global.response.PageResponse;
import nct.provider.dto.ProviderProfileSearchItem;

/**
 * 담당자 7 · F-COM-002: 다른 도메인이 PROVIDER_PROFILE Mapper를 직접 사용하지 않도록 제공하는
 * 읽기 전용 검색 계약이다.
 */
public interface ProviderProfileSearchReader {
    PageResponse<ProviderProfileSearchItem> searchApprovedProfiles(ProviderProfileSearchCriteria criteria);
}
