package nct.provider.port;

/**
 * 담당자 6 · F-COM-002: 담당자 5의 서비스 탐색이 전달하는 제공자 검색 조건이다.
 * (헤더의 "담당자 7" 표기는 업무분장 변경 전 잔재라 정정 — 2026-08-05)
 *
 * <p>page는 0부터 시작한다. 페이지 응답 규격은 팀 공통 규격(global/response/PageResponse)으로
 * 이미 확정·통용되고 있다 — "확정 후 교체" 문구는 낡은 표현이라 정정 (2026-08-05).</p>
 */
public record ProviderProfileSearchCriteria(
        String keyword,
        Long categorySn,
        String region,
        String sort,
        int page,
        int size) {
}
