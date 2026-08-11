package nct.quote.dto;

/** 담당자 7 · F-PROV-009: 제공자 대시보드에 표시할 상태별 견적 수입니다. */
public record MyQuoteSummaryResponse(
        int totalQuoteCount,
        int activeQuoteCount,
        int selectedQuoteCount,
        int endedQuoteCount) {}
