package nct.auction.port.stub;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import nct.auction.port.TradeCreationPort;

import lombok.extern.slf4j.Slf4j;

/**
 * ================================================================================
 *  [임시 구현체 - TODO: 담당자4 실제 구현체로 교체 필요]
 * ================================================================================
 * - TRADE 테이블의 고정 기술 소유자는 담당자4 이다. 이 클래스는 실제 TRADE 행을
 *   생성하지 않고, "거래가 생성됐다고 치면 이런 번호가 나왔을 것이다"라는 가짜 번호만 돌려준다.
 * - 담당자4가 실제 TradeCreationPort 구현체를 Bean 으로 등록하면 이 파일은 반드시 삭제한다.
 * ================================================================================
 */
@Slf4j
@Component
public class StubTradeCreationPort implements TradeCreationPort {

    // 호출할 때마다 다른 값을 주기 위한 임시 채번기 (실제 TRD_SN 과 절대 무관함을 강조하기 위해
    // 실제 PK 체계와 겹치지 않는 음수 영역을 사용한다).
    private final AtomicLong fakeSequence = new AtomicLong(-1L);

    @Override
    public Long createTradeForBuyNow(Long aucSn, Long prdSn, Long sellerUsrSn, Long buyerUsrSn, Long finalAmt) {
        long fakeTradeSn = fakeSequence.getAndDecrement();
        log.warn("[STUB] StubTradeCreationPort.createTradeForBuyNow() 호출됨 " +
                 "(aucSn={}, prdSn={}, sellerUsrSn={}, buyerUsrSn={}, finalAmt={}) - " +
                 "실제 TRADE 행을 생성하지 않고 가짜 번호({})만 반환합니다. " +
                 "담당자4의 실제 거래 생성 구현체로 반드시 교체해야 합니다.",
                 aucSn, prdSn, sellerUsrSn, buyerUsrSn, finalAmt, fakeTradeSn);
        return fakeTradeSn;
    }
}
