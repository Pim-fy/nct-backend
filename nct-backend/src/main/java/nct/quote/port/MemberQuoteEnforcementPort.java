package nct.quote.port;

import java.util.List;

/** 담당자 7 · 신고 제재: 영구정지 회원과 연관된 견적을 안전하게 철회하는 계약입니다. */
public interface MemberQuoteEnforcementPort {

    List<QuoteEnforcementImpact> withdrawActiveQuotes(
            Long userSn,
            Long adminUserSn,
            String reason);
}
