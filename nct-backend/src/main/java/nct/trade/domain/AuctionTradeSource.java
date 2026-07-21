package nct.trade.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 경매 종료 방식에 따라 거래를 만든 원인을 구분한다. */
@Getter
@RequiredArgsConstructor
public enum AuctionTradeSource {

    BUY_NOW("즉시구매로 거래가 생성되었습니다."),
    AUCTION_WIN("자동 낙찰로 거래가 생성되었습니다.");

    /** 최초 거래 상태 이력에 남길, 경매 도메인에서 확정한 생성 사유다. */
    private final String statusHistoryReason;
}
