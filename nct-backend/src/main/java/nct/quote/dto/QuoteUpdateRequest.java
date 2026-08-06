package nct.quote.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuoteUpdateRequest(
        @NotNull @Positive Long amount,
        @Size(max = 4000) String content,
        @Size(max = 100) String duration,
        List<Long> photoFlSns) {
}
