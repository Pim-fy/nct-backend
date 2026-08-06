package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.AuctionBidTradeReaderService;

class AuctionBidTradeReaderTest {

    private TradeMapper tradeMapper;
    private AuctionBidTradeReaderService reader;

    @BeforeEach
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        reader = new AuctionBidTradeReaderService(tradeMapper);
    }

    @Test
    void returnsOnlyOwnedMaterialTradeReferencesForRequestedBids() {
        AuctionBidTradeReference reference = new AuctionBidTradeReference();
        reference.setBidSn(501L);
        reference.setTradeId(91L);
        when(tradeMapper.findAuctionBidTradeReferencesByBuyerAndBidSns(10L, List.of(501L, 502L)))
                .thenReturn(List.of(reference));

        List<AuctionBidTradeReference> result = reader.findByBuyerAndBidSns(10L, List.of(501L, 502L));

        assertThat(result).containsExactly(reference);
        verify(tradeMapper).findAuctionBidTradeReferencesByBuyerAndBidSns(10L, List.of(501L, 502L));
    }

    @Test
    void returnsEmptyListWithoutQueryingMapperWhenBidNumbersAreEmpty() {
        assertThat(reader.findByBuyerAndBidSns(10L, List.of())).isEmpty();

        verifyNoInteractions(tradeMapper);
    }

    @Test
    void rejectsInvalidBuyerOrBidNumberBeforeQueryingMapper() {
        assertThatThrownBy(() -> reader.findByBuyerAndBidSns(0L, List.of(501L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> reader.findByBuyerAndBidSns(10L, List.of(0L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(tradeMapper);
    }
}
