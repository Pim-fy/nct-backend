package nct.trade.port;

import java.math.BigDecimal;

/** 견적 도메인이 잠금·선택 상태 검증 뒤 거래 도메인에 제공하는 서버 원본 데이터다. */
public record SelectedServiceQuote(
        long serviceRequestId,
        long quoteId,
        long requesterUserId,
        long providerUserId,
        BigDecimal quoteAmount) {
}
