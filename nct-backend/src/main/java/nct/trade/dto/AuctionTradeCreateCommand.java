package nct.trade.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nct.trade.domain.AuctionTradeSource;

/**
 * AuctionService가 행 잠금·낙찰자·금액 검증을 완료한 뒤 전달하는 내부 거래 생성 명령이다.
 * 거래 방식은 클라이언트 또는 이 명령의 값이 아니라 PRODUCT에서 다시 조회해 결정한다.
 */
@Getter
@AllArgsConstructor
public class AuctionTradeCreateCommand {

    private final long auctionId;
    private final long productId;
    private final long winningBidId;
    private final long sellerUserId;
    private final long buyerUserId;
    private final BigDecimal tradeAmount;
    private final AuctionTradeSource source;
}
