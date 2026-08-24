package nct.quote.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuoteUpdateRequest(
        @Size(max = 50) String title,
        @NotNull @Min(0) @Max(1000000000) Long amount,
        @Size(max = 4000) String content,
        @NotEmpty @Size(max = 5) List<Long> photoFlSns) {
}
