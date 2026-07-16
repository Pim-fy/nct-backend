package nct.settlement.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Settlement {

    private Long stlmSn;
    private Long trdSn;
    private Long usrSn;
    private long stlmAmt;
    private String stlmStatusCd;
    private LocalDateTime stlmRegDt;
    private LocalDateTime stlmUpdtDt;
}
