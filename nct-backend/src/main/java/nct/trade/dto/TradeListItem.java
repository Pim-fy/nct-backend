package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 거래내역 화면 한 행에 필요한 조회 전용 데이터다. */
@Data
public class TradeListItem {

    private Long tradeId;
    private String userRole;
    private String productName;
    private String counterpartNickname;
    private BigDecimal tradeAmount;
    private LocalDateTime createdAt;
    private String tradeStatus;
    private String tradeMethod;
}
