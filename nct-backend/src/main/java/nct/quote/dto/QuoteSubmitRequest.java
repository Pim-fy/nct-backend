package nct.quote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuoteSubmitRequest(
        @NotNull Long svcReqSn,
        @NotNull @Positive Long amount,
        @Size(max = 4000) String content) {
}
