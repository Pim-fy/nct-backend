package nct.abuse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerSupportReportRequest(
        @NotBlank String targetType,
        @NotNull @Positive Long targetSn,
        @NotBlank @Size(max = 500) String reportContent) {
}
