package nct.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-PROV-009: 제공자 견적 상태를 한 번에 검증하기 위한 Mapper 전용 집계값입니다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyQuoteSummaryCounts {
    private int totalQuoteCount;
    private int activeQuoteCount;
    private int selectedQuoteCount;
    private int endedQuoteCount;
    private int unsupportedQuoteCount;
}
