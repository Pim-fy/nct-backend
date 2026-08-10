package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 서비스 거래 상세 화면과 API가 함께 사용하는 역할별 조회 응답이다. */
public record ServiceTradeDetailResponse(
        long tradeId,
        long serviceRequestId,
        String viewerRole,
        String tradeStatusCode,
        BigDecimal tradeAmount,
        LocalDateTime autoCompleteAt,
        String serviceRequestTitle,
        String quoteSummary,
        String scheduleLabel,
        String escrowStatusCode,
        String escrowStatusLabel,
        boolean chatAvailable,
        List<ServiceScheduleHistoryItem> scheduleHistory,
        List<String> availableActions) {
}
