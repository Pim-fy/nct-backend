package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;
import nct.auction.service.BidService;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.port.AuctionBidTradeReader;

@ExtendWith(MockitoExtension.class)
class BidServiceTradeLinkTest {

    @Mock
    private BidMapper bidMapper;

    @Mock
    private AuctionBidTradeReader auctionBidTradeReader;

    @InjectMocks
    private BidService bidService;

    @Test
    void mapsTradeIdOnlyForWonBids() {
        MyBidHistoryItem won = historyItem(101L, BidStatusCode.HIGHEST, AuctionStatusCode.ENDED);
        MyBidHistoryItem active = historyItem(102L, BidStatusCode.HIGHEST, AuctionStatusCode.ACTIVE);
        AuctionBidTradeReference reference = tradeReference(101L, 202L);

        when(bidMapper.findMyBidHistory(77L)).thenReturn(List.of(won, active));
        when(auctionBidTradeReader.findByBuyerAndBidSns(77L, List.of(101L)))
                .thenReturn(List.of(reference));

        List<MyBidHistoryItem> result = bidService.getMyBidHistory(77L);

        assertThat(result).containsExactly(won, active);
        assertThat(won.getTradeId()).isEqualTo(202L);
        assertThat(active.getTradeId()).isNull();
        verify(auctionBidTradeReader).findByBuyerAndBidSns(77L, List.of(101L));
    }

    @Test
    void skipsTradeLookupWhenThereAreNoWonBids() {
        MyBidHistoryItem active = historyItem(102L, BidStatusCode.HIGHEST, AuctionStatusCode.ACTIVE);
        when(bidMapper.findMyBidHistory(77L)).thenReturn(List.of(active));

        List<MyBidHistoryItem> result = bidService.getMyBidHistory(77L);

        assertThat(result).containsExactly(active);
        assertThat(active.getTradeId()).isNull();
        verifyNoInteractions(auctionBidTradeReader);
    }

    private MyBidHistoryItem historyItem(long bidSn, String bidStatusCode, String auctionStatusCode) {
        MyBidHistoryItem item = new MyBidHistoryItem();
        item.setBidSn(bidSn);
        item.setBidStatusCode(bidStatusCode);
        item.setAuctionStatusCode(auctionStatusCode);
        return item;
    }

    private AuctionBidTradeReference tradeReference(long bidSn, long tradeId) {
        AuctionBidTradeReference reference = new AuctionBidTradeReference();
        reference.setBidSn(bidSn);
        reference.setTradeId(tradeId);
        return reference;
    }
}
