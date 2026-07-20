package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.MyBidHistoryItem;

class MyBidHistoryItemTest {

    @Test
    @DisplayName("상태 판정: 진행 중 최고입찰은 HIGHEST로 내려간다")
    void highest() {
        MyBidHistoryItem item = item(BidStatusCode.HIGHEST, AuctionStatusCode.ACTIVE);

        assertThat(item.resolveDisplayStatus()).isEqualTo("HIGHEST");
    }

    @Test
    @DisplayName("상태 판정: 상위입찰에서 밀린 입찰은 OUTBID로 내려간다")
    void outbid() {
        MyBidHistoryItem item = item(BidStatusCode.OUTBID, AuctionStatusCode.ACTIVE);

        assertThat(item.resolveDisplayStatus()).isEqualTo("OUTBID");
    }

    @Test
    @DisplayName("상태 판정: 최고입찰 상태로 낙찰 확정된 경매는 WON으로 내려간다")
    void won() {
        MyBidHistoryItem item = item(BidStatusCode.HIGHEST, AuctionStatusCode.ENDED);

        assertThat(item.resolveDisplayStatus()).isEqualTo("WON");
    }

    @Test
    @DisplayName("상태 판정: 입찰 취소 또는 유찰·취소 경매는 CANCELED로 내려간다")
    void canceled() {
        assertThat(item(BidStatusCode.CANCELED, AuctionStatusCode.ACTIVE).resolveDisplayStatus())
                .isEqualTo("CANCELED");
        assertThat(item(BidStatusCode.EXCEPTION_CANCELED, AuctionStatusCode.ACTIVE).resolveDisplayStatus())
                .isEqualTo("CANCELED");
        assertThat(item(BidStatusCode.HIGHEST, AuctionStatusCode.FAILED).resolveDisplayStatus())
                .isEqualTo("CANCELED");
        assertThat(item(BidStatusCode.HIGHEST, AuctionStatusCode.CANCELED).resolveDisplayStatus())
                .isEqualTo("CANCELED");
    }

    private MyBidHistoryItem item(String bidStatusCode, String auctionStatusCode) {
        MyBidHistoryItem item = new MyBidHistoryItem();
        item.setBidStatusCode(bidStatusCode);
        item.setAuctionStatusCode(auctionStatusCode);
        return item;
    }
}
