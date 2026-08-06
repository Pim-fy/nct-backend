package nct.quote.port;

/**
 * 거래 생성용 선택 견적 잠금·검증 포트 — 정민재(담당자4) 서비스 거래 생성 트랜잭션에서 호출.
 * 이미 QUTC0004(선택됨) 상태인 견적을 FOR UPDATE로 잠그고 검증 후 반환.
 * 호출자의 트랜잭션에 참여(REQUIRED)하므로 거래 생성 실패 시 함께 롤백.
 */
public interface SelectedServiceQuoteReader {

    SelectedServiceQuoteTarget lockSelectedQuoteForTradeCreation(
            Long requesterUserId,
            Long serviceRequestId,
            Long quoteId);

    record SelectedServiceQuoteTarget(
            Long serviceRequestId,
            Long quoteId,
            Long requesterUserId,
            Long providerUserId,
            Long quoteAmount,
            String quoteStatusCode) {}
}
