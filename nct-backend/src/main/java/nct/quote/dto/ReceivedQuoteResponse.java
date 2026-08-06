package nct.quote.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedQuoteResponse {

    private Long qutSn;
    private Long providerUsrSn;
    private String providerNm;
    private Long amount;
    private String content;
    private String statusCode;
    private int reviseCnt;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private List<QuoteAttachmentResponse> attachments;
}
