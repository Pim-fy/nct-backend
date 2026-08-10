package nct.abuse.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;

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
    private String statusCode;
    private String title;
    private String targetName;
    private String content;
    private String referenceTypeCode;
    private Long referenceSn;
    private String processReason;
    private String processedBy;
    private AdminMemberIdentityResponse processorMember;
    private LocalDateTime registeredAt;
    private LocalDateTime processedAt;

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
