package nct.abuse.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminAbuseReportResponse {

    private Long reportSn;
    private Long riskEventSn;
    private Long reporterUserSn;
    private Long reportedUserSn;
    private String reportTypeCode;
    private String statusCode;
    private String content;
    private String referenceTypeCode;
    private Long referenceSn;
    private String processReason;
    private LocalDateTime registeredAt;
    private LocalDateTime processedAt;
}
