package nct.quote.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 3 견적 조회 계약 / 담당자 7 F-OPS-021 소비: 관리자 서비스 요청 상세에 필요한
 * 비민감 견적 원천 정보만 제공합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminQuoteListItem {

    private Long serviceRequestId;
    private Long quoteId;
    private Long providerUserId;
    private Long amount;
    private String statusCode;
    private int reviseCount;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
}
