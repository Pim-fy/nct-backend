package nct.quote.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotePhoto {

    private Long qutPhtSn;
    private Long qutSn;
    private Long flSn;
    private int sortNo;
}
