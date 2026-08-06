package nct.quote.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuoteSubmitRequest(
        @NotNull Long svcReqSn,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Positive Long amount,
        @Size(max = 4000) String content,
        List<Long> photoFlSns) {
}
