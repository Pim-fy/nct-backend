package nct.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private Long prdSn;
    private Long usrSn;
    private Long catSn;
    private String catNm;
    private String prdNm;
    private String prdCn;
    private String prdStatusCd;
    private BigDecimal prdStartAmt;
    private BigDecimal prdIbyAmt;
    private String prdTrdMethodCd;
    private LocalDateTime prdRegDt;
    private LocalDateTime prdUpdtDt;
}
