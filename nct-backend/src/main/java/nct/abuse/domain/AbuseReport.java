package nct.abuse.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbuseReport {

    private Long reportSn;
    private Long riskEventSn;
    private Long reporterUserSn;
    private Long reportedUserSn;
    private String reportTypeCode;
    private String statusCode;
    private String referenceTypeCode;
    private Long referenceSn;
    private String content;
    private String processReason;
    private String registeredBy;
    private String updatedBy;
}
