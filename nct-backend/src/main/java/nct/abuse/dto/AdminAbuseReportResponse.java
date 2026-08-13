package nct.abuse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.ops.sanction.dto.SanctionImpactResponse;

@Getter
@Setter
@NoArgsConstructor
public class AdminAbuseReportResponse {

    private Long reportSn;
    private Long riskEventSn;
    private Long reporterUserSn;
    private Long reportedUserSn;
    private AdminMemberIdentityResponse reporterMember;
    private AdminMemberIdentityResponse reportedMember;
    private String reportTypeCode;
    private String reportTypeName;
    /** 일반 신고 또는 거래 문제 분류값(GENERAL/TRADE_ISSUE) */
    private String caseType;
    private String statusCode;
    private String title;
    private String targetName;
    private String content;
    private String referenceTypeCode;
    private Long referenceSn;
    /** 담당자 7 · F-OPS-007: 관리자가 참조 번호만 보고 원본을 추측하지 않도록 조회 시 조립합니다. */
    private String referenceTitle;
    private String referenceDetailType;
    private Long referenceDetailSn;
    private String processReason;
    private Long tradeSn;
    private Long productSn;
    private Long serviceRequestSn;
    private Long quoteSn;
    private BigDecimal tradeAmount;
    private String tradeTypeCode;
    private String tradeTypeName;
    private String tradeMethodCode;
    private String tradeMethodName;
    private String tradeStatusCode;
    private String tradeStatusName;
    private String tradeResultCode;
    private String tradeResultName;
    private String previousTradeStatusCode;
    private Long remainingAutoCompleteSeconds;
    private boolean settlementHoldApplied;
    private boolean chatClosed;
    private LocalDateTime tradeAutoCompleteAt;
    private String enforcementAction;
    private Long sanctionSn;
    private LocalDateTime sanctionStartedAt;
    private LocalDateTime sanctionEndedAt;
    private boolean sanctionReleased;
    private List<SanctionImpactResponse> sanctionImpacts;
    private String processedBy;
    private AdminMemberIdentityResponse processorMember;
    private LocalDateTime registeredAt;
    private LocalDateTime processedAt;
    private List<AbuseReportFileResponse> files;

    public AdminAbuseReportResponse(
            Long reportSn,
            Long riskEventSn,
            Long reporterUserSn,
            Long reportedUserSn,
            String reportTypeCode,
            String statusCode,
            String title,
            String targetName,
            String content,
            String referenceTypeCode,
            Long referenceSn,
            String processReason,
            String processedBy,
            LocalDateTime registeredAt,
            LocalDateTime processedAt) {
        this.reportSn = reportSn;
        this.riskEventSn = riskEventSn;
        this.reporterUserSn = reporterUserSn;
        this.reportedUserSn = reportedUserSn;
        this.reportTypeCode = reportTypeCode;
        this.statusCode = statusCode;
        this.title = title;
        this.targetName = targetName;
        this.content = content;
        this.referenceTypeCode = referenceTypeCode;
        this.referenceSn = referenceSn;
        this.processReason = processReason;
        this.processedBy = processedBy;
        this.registeredAt = registeredAt;
        this.processedAt = processedAt;
    }
}
