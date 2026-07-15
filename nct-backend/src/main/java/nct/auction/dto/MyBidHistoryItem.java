package nct.auction.dto;

import java.time.LocalDateTime;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [F-AUC-022 내 입찰 내역 조회 - 한 행 응답 DTO]
 * - BID 와 AUCTION 을 JOIN 한 조회 전용 DTO (도메인 객체 Bid/Auction 을 그대로 쓰지 않는 이유:
 *   이 화면에는 "그 입찰이 지금 어떤 의미인지" 판단하려면 AUCTION 의 상태도 함께 필요하기 때문).
 *
 * "낙찰" 상태에 대한 참고:
 *   로직정의서는 F-AUC-022 에서 "입찰 성공/최고입찰/반환/낙찰" 4가지 상태를 보여줘야 한다고
 *   명시하지만, "낙찰(WON)"을 BID_STATUS_CD 에 확정하는 일 자체는 F-AUC-020(경매 종료/낙찰 확정,
 *   담당자5 소유)의 몫이다. 지금은 BID_STATUS_CD 가 ACTIVE/OUTBID 두 가지뿐이므로,
 *   "경매가 이미 종료(ENDED)됐는데 이 입찰이 아직 ACTIVE" 인 경우를 여기서 "낙찰"로 잠정 해석한다.
 *   담당자5가 낙찰 전용 BID 상태를 확정하면 이 로직은 그 상태값을 직접 참조하도록 단순화될 수 있다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyBidHistoryItem {

    private Long bidSn;
    private Long aucSn;
    private Long bidAmt;
    private String bidStatusCd;   // BID.BID_STATUS_CD (ACTIVE/OUTBID)
    private String auctionStatusCd; // AUCTION.AUC_STATUS_CD (IN_PROGRESS/ENDED) - "낙찰" 해석에 필요
    private LocalDateTime bidRegDt;

    /**
     * 화면에 보여줄 상태 라벨을 서버가 계산해서 내려준다 (프론트가 코드 조합을 해석하지 않게 하기 위함).
     * @return "WON"(낙찰, 잠정) | "HIGHEST"(진행중 최고입찰) | "OUTBID"(반환됨)
     */
    public String resolveDisplayStatus() {
        if (BidStatusCode.OUTBID.equals(bidStatusCd)) {
            return "OUTBID";
        }
        // BidStatusCode.ACTIVE 인 경우 - 경매가 이미 끝났으면 "낙찰"로, 아직 진행 중이면 "최고입찰"로 본다.
        if (AuctionStatusCode.ENDED.equals(auctionStatusCd)) {
            return "WON";
        }
        return "HIGHEST";
    }
}
