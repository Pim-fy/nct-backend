package nct.quote.port;

/**
 * 견적 선택 포트 — 신현석(담당자2) 견적 선택 트랜잭션에서 호출.
 * 이 포트 구현체는 호출자의 트랜잭션에 참여(REQUIRED)하므로 별도 커밋 없이 롤백 가능.
 *
 * 처리 순서:
 *   1. 선택 견적 QUTC0004(선택됨)으로 상태 전이
 *   2. 같은 서비스 요청의 경쟁 견적 QUTC0005(철회)로 일괄 처리
 *
 * SERVICE_REQUEST 상태 전환(SVCC0002→SVCC0003)은 호출자(담당자2) 책임.
 */
public interface QuoteSelectionPort {

    SelectedQuoteResult selectQuote(Long quoteId, Long svcReqSn, Long requesterUsrSn);

    record SelectedQuoteResult(Long qutSn, Long providerUsrSn, Long amount) {}
}
