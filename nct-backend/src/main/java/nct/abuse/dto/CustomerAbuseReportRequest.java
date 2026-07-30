package nct.abuse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerAbuseReportRequest(
        @NotBlank @Size(max = 30) String reportTypeCode,
        Long reportedUserSn,
        @Size(max = 30) String referenceTypeCode,
        @Positive Long referenceSn,
        @Size(max = 200) String targetName,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 4000) String content) {
}
