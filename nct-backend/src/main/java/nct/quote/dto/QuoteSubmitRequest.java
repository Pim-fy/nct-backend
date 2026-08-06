package nct.quote.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuoteSubmitRequest(
        @NotNull Long svcReqSn,
        @NotNull @Positive Long amount,
        @Size(max = 4000) String content,
        @NotEmpty @Size(max = 5) List<Long> photoFlSns) {
}
