package nct.quote.port;

import java.util.List;

/** 담당자 7 · 신고 제재: 기간 제재 견적 보류와 영구정지 견적 철회를 분리한 계약입니다. */
public interface MemberQuoteEnforcementPort {

    List<QuoteEnforcementImpact> pauseActiveQuotes(
            Long userSn,
            Long adminUserSn,
            String reason);

    List<QuoteEnforcementImpact> withdrawActiveQuotes(
            Long userSn,
            Long adminUserSn,
            String reason);

    boolean restorePausedQuote(
            Long quoteId,
            String previousStatusCode,
            Long adminUserSn);
}
