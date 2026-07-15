package nct.auction.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import nct.auction.port.TradeCreationPort;

/**
 * [담당자4(거래) 계약의 Fake 구현체]
 * - 실제 TRADE 저장 없이 "호출된 인자"를 기록하고 순번을 발급한다.
 * - 테스트에서 "즉시구매 성공 시 정확히 이 판매자·구매자·금액으로 거래 생성을 요청했는가"를 검증할 때 쓴다.
 */
public class FakeTradeCreationPort implements TradeCreationPort {

    private final AtomicLong sequence = new AtomicLong(1L);
    public final List<CreatedTrade> createdTrades = new ArrayList<>();

    public record CreatedTrade(Long aucSn, Long prdSn, Long sellerUsrSn, Long buyerUsrSn, Long finalAmt) {
    }

    @Override
    public Long createTradeForBuyNow(Long aucSn, Long prdSn, Long sellerUsrSn, Long buyerUsrSn, Long finalAmt) {
        createdTrades.add(new CreatedTrade(aucSn, prdSn, sellerUsrSn, buyerUsrSn, finalAmt));
        return sequence.getAndIncrement();
    }
}
