package nct.trade.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 견적 선택과 보관금 생성이 서버에서 확정된 뒤에만 사용하는 내부 서비스 거래 생성 계약이다.
 * HTTP 요청 DTO가 아니므로 클라이언트가 전달한 회원번호·금액을 그대로 담아 호출해서는 안 된다.
 */
@Getter
@AllArgsConstructor
public class ServiceTradeCreateCommand {

    private final long requesterUserId;
    private final long providerUserId;
    private final long serviceRequestId;
    private final long selectedQuoteId;
    private final BigDecimal tradeAmount;
}
