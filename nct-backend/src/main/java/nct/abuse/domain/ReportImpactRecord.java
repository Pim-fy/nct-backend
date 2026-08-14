package nct.abuse.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 7 · F-OPS-007: 신고 접수로 보류된 단건 대상의 복구 기준을 보존합니다. */
@Getter
@NoArgsConstructor
public class ReportImpactRecord {

    private Long impactSn;
    private Long reportSn;
    private String referenceTypeCode;
    private Long referenceSn;
    private String actionCode;
    private String statusCode;
    private String previousStatusCode;
    private LocalDateTime previousStartAt;
    private LocalDateTime previousDeadlineAt;
    private Long remainingStartSeconds;
    private Long remainingSeconds;
    private boolean settlementHoldApplied;
    private String result;
    private String registeredBy;
    private String updatedBy;

    @Builder
    public ReportImpactRecord(
            Long impactSn,
            Long reportSn,
            String referenceTypeCode,
            Long referenceSn,
            String actionCode,
            String statusCode,
            String previousStatusCode,
            LocalDateTime previousStartAt,
            LocalDateTime previousDeadlineAt,
            Long remainingStartSeconds,
            Long remainingSeconds,
            boolean settlementHoldApplied,
            String result,
            String registeredBy,
            String updatedBy) {
        this.impactSn = impactSn;
        this.reportSn = reportSn;
        this.referenceTypeCode = referenceTypeCode;
        this.referenceSn = referenceSn;
        this.actionCode = actionCode;
        this.statusCode = statusCode;
        this.previousStatusCode = previousStatusCode;
        this.previousStartAt = previousStartAt;
        this.previousDeadlineAt = previousDeadlineAt;
        this.remainingStartSeconds = remainingStartSeconds;
        this.remainingSeconds = remainingSeconds;
        this.settlementHoldApplied = settlementHoldApplied;
        this.result = result;
        this.registeredBy = registeredBy;
        this.updatedBy = updatedBy;
    }
}
