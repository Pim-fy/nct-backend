package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 서비스 거래 상세 조립에 필요한 서버 원본 데이터다.
 * 서비스 요청·견적·보관금 조회 구현체는 계약 확정 뒤 이 형태로 데이터를 제공한다.
 */
public record ServiceTradeDetailSource(
        long tradeId,
        long requesterUserId,
        long providerUserId,
        long serviceRequestId,
        String tradeStatusCode,
        BigDecimal tradeAmount,
        LocalDateTime autoCompleteAt,
        String serviceRequestTitle,
        String quoteSummary,
        String scheduleLabel,
        String escrowStatusCode,
        String escrowStatusLabel) {
}
