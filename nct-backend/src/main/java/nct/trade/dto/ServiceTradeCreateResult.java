package nct.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 선택 견적 기준의 서비스 거래 생성·재호출 결과다. */
@Getter
@AllArgsConstructor
public class ServiceTradeCreateResult {

    private final long tradeId;
    private final String tradeStatusCode;
    private final boolean created;
}
