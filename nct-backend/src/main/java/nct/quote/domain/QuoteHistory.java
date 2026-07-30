package nct.quote.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteHistory {

    private Long qutHstSn;
    private Long qutSn;
    private Long qutHstAmt;
    private String qutHstCn;
    private String qutHstRegId;
    private String qutHstUpdtId;
}
