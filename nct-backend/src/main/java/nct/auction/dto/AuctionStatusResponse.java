package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionStatusResponse {

    private Long aucSn;
    private Long prdSn;
    private String aucStatusCd;
    private BigDecimal aucCurAmt;
    private Long bidCount;
    private LocalDateTime aucStartDt;
    private LocalDateTime aucEndDt;
    private Integer aucExtCnt;
}
