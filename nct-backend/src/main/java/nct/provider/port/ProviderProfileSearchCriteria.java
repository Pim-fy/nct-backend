package nct.provider.port;

/**
 * 담당자 7 · F-COM-002: 담당자 5의 서비스 탐색이 전달하는 제공자 검색 조건이다.
 *
 * <p>page는 0부터 시작하며, 현재 페이지 응답 규격은 팀 공통 규격 확정 후 교체할 수 있다.</p>
 */
public record ProviderProfileSearchCriteria(
        String keyword,
        Long categorySn,
        String region,
        String sort,
        int page,
        int size) {
}
