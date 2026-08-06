package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** F-AUC-005 화면이 판매 상품별 생성된 거래 상태를 결합할 때 쓰는 조회 항목이다. */
@Data
public class SellerTradeStatusItem {

    private Long prdSn;
    private Long tradeSn;
    private String tradeStatusCd;
    private String tradeMethodCd;
    private BigDecimal tradeAmount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
