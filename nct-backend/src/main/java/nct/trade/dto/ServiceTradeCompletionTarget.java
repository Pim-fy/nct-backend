package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 서비스 완료 요청·확인·자동 완료에서 잠근 서비스 거래의 서버 기준 정보다. */
@Data
public class ServiceTradeCompletionTarget {

    private long tradeId;
    private long requesterUserId;
    private long providerUserId;
    private BigDecimal tradeAmount;
    private String tradeStatus;
    private LocalDateTime autoCompleteAt;
}
