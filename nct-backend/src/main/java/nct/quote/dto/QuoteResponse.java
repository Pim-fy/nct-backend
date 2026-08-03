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
public class QuoteResponse {

    private Long qutSn;
    private Long svcReqSn;
    private String svcReqTitle;
    private Long amount;
    private String content;
    private String statusCode;
    private int reviseCnt;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
