package nct.quote.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-021: 관리자 통합상태 API에 제공하는 서비스 요청별 견적 요약입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminQuoteSummary {
    private Long serviceRequestId;
    private int totalQuoteCount;
    private int activeQuoteCount;
    private int selectedQuoteCount;
    private int unsupportedQuoteCount;
    private Long selectedQuoteId;
    private Long selectedProviderUserId;
    private Long selectedAmount;
    private String selectedQuoteStatusCode;
}
