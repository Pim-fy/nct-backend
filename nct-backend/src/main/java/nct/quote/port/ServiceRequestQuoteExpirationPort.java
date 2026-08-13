package nct.quote.port;

/**
 * 담당자 7 통합 · F-SVC-003: 요청서 마감과 함께 선택 전 활성 견적을 만료시키는 공개 계약입니다.
 * 요청서 도메인은 QUOTE Mapper를 직접 호출하지 않고 이 계약만 소비합니다.
 */
public interface ServiceRequestQuoteExpirationPort {

    int expireActiveQuotes(Long serviceRequestId, String actorId);
}
