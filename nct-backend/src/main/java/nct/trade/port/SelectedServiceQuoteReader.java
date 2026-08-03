package nct.trade.port;

/**
 * 담당자3 견적 도메인 어댑터 계약이다.
 * 구현체는 같은 상위 트랜잭션에서 선택 견적 행을 잠그고, 요청자 소유·선택 상태를 재검증해야 한다.
 */
public interface SelectedServiceQuoteReader {

    SelectedServiceQuote lockSelectedQuoteForTradeCreation(
            long requesterUserId,
            long serviceRequestId,
            long quoteId);
}
