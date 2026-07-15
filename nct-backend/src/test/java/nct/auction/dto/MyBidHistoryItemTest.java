package nct.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;

/**
 * [F-AUC-022 상태 라벨 계산 로직 단위 테스트]
 * - resolveDisplayStatus() 는 DB 조회 없이 필드 조합만으로 계산하는 순수 함수라
 *   Mock 이 전혀 필요 없다. 이런 경우가 가장 테스트하기 쉬운 코드다.
 */
class MyBidHistoryItemTest {

    private MyBidHistoryItem itemOf(String bidStatusCd, String auctionStatusCd) {
        return MyBidHistoryItem.builder()
                .bidStatusCd(bidStatusCd)
                .auctionStatusCd(auctionStatusCd)
                .build();
    }

    @Test
    void 반환된_입찰은_경매_상태와_무관하게_OUTBID로_표시된다() {
        MyBidHistoryItem item = itemOf(BidStatusCode.OUTBID, AuctionStatusCode.IN_PROGRESS);
        assertThat(item.resolveDisplayStatus()).isEqualTo("OUTBID");
    }

    @Test
    void 진행중인_경매의_ACTIVE_입찰은_HIGHEST로_표시된다() {
        MyBidHistoryItem item = itemOf(BidStatusCode.ACTIVE, AuctionStatusCode.IN_PROGRESS);
        assertThat(item.resolveDisplayStatus()).isEqualTo("HIGHEST");
    }

    @Test
    void 종료된_경매의_ACTIVE_입찰은_WON으로_잠정_해석된다() {
        // F-AUC-020(낙찰 확정, 담당자5)이 전용 상태를 만들기 전까지의 잠정 해석
        MyBidHistoryItem item = itemOf(BidStatusCode.ACTIVE, AuctionStatusCode.ENDED);
        assertThat(item.resolveDisplayStatus()).isEqualTo("WON");
    }
}
