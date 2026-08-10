package nct.trade.dto;

import java.math.BigDecimal;

import lombok.Getter;
import nct.trade.domain.AuctionTradeSource;

/**
 * AuctionService가 행 잠금·낙찰자·금액 검증을 완료한 뒤 전달하는 내부 거래 생성 명령이다.
 * 선택 거래방식은 잠긴 경매·입찰 행을 검증한 AuctionService만 설정한다.
 */
@Getter
public class AuctionTradeCreateCommand {

    private final long auctionId;
    private final long productId;
    private final long winningBidId;
    private final long sellerUserId;
    private final long buyerUserId;
    private final BigDecimal tradeAmount;
    private final AuctionTradeSource source;
    /** 실제 거래방식. 혼합 상품(TRDC0020)은 택배 또는 직거래 중 하나여야 한다. */
    private final String selectedTradeMethodCode;
    /** 배송 거래일 때 입찰자가 선택한 USER_DELIVERY_ADDRESS 식별자다. */
    private final Long selectedDeliveryAddressId;

    /** 기존 단일 거래방식 상품 호출과의 호환용 생성자다. */
    public AuctionTradeCreateCommand(
            long auctionId,
            long productId,
            long winningBidId,
            long sellerUserId,
            long buyerUserId,
            BigDecimal tradeAmount,
            AuctionTradeSource source) {
        this(auctionId, productId, winningBidId, sellerUserId, buyerUserId,
                tradeAmount, source, null, null);
    }

    public AuctionTradeCreateCommand(
            long auctionId,
            long productId,
            long winningBidId,
            long sellerUserId,
            long buyerUserId,
            BigDecimal tradeAmount,
            AuctionTradeSource source,
            String selectedTradeMethodCode) {
        this(auctionId, productId, winningBidId, sellerUserId, buyerUserId,
                tradeAmount, source, selectedTradeMethodCode, null);
    }

    public AuctionTradeCreateCommand(
            long auctionId,
            long productId,
            long winningBidId,
            long sellerUserId,
            long buyerUserId,
            BigDecimal tradeAmount,
            AuctionTradeSource source,
            String selectedTradeMethodCode,
            Long selectedDeliveryAddressId) {
        this.auctionId = auctionId;
        this.productId = productId;
        this.winningBidId = winningBidId;
        this.sellerUserId = sellerUserId;
        this.buyerUserId = buyerUserId;
        this.tradeAmount = tradeAmount;
        this.source = source;
        this.selectedTradeMethodCode = selectedTradeMethodCode;
        this.selectedDeliveryAddressId = selectedDeliveryAddressId;
    }
}
