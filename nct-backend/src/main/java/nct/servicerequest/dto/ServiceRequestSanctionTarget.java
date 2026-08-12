package nct.servicerequest.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · 신고 제재: 회원 소유 요청서의 중지·취소 판단용 잠금 조회 결과입니다. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestSanctionTarget {

    private Long serviceRequestId;
    private Long ownerUserSn;
    private String statusCode;
    private Long linkedTradeSn;
    private String linkedTradeStatusCode;
    private LocalDateTime effectiveDeadlineAt;
    private LocalDateTime databaseNow;
}
