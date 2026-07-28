package nct.abuse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ManualAbuseReportRequest(
        @NotNull @Positive Long reportedUserSn,
        @NotBlank @Size(max = 30) String referenceTypeCode,
        @NotNull @Positive Long referenceSn,
        @Size(max = 4000) String content) {
}
