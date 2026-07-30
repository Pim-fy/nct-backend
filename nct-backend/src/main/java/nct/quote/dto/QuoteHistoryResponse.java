package nct.quote.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuoteHistoryResponse {

    private Long qutHstSn;
    private Long qutSn;
    private Long amount;
    private String content;
    private LocalDateTime registeredAt;
}
