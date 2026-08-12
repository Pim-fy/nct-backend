package nct.quote.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · 신고 제재: 영구정지 회원의 활성 견적 철회 판단용 잠금 조회 결과입니다. */
@Getter
@Setter
@NoArgsConstructor
public class QuoteSanctionTarget {

    private Long quoteId;
    private Long serviceRequestId;
    private Long providerUserSn;
    private Long requestOwnerUserSn;
    private String statusCode;
    private String linkedTradeStatusCode;
}
