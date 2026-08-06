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
public class Quote {

    private Long qutSn;
    private Long svcReqSn;
    private Long usrSn;
    private Long qutAmt;
    private String qutCn;
    private String qutEstmDrt;
    private String qutStatusCd;
    private int qutReviseCnt;
    private String qutRegId;
    private String qutUpdtId;
}
