package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.TradeSettlementReferenceReaderService;

class TradeSettlementReferenceReaderTest {

    private TradeMapper tradeMapper;
    private TradeSettlementReferenceReaderService reader;

    @BeforeEach
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        reader = new TradeSettlementReferenceReaderService(tradeMapper);
    }

    @Test
    void returnsMaterialTradeTypeAndOriginalBidReference() {
        TradeSettlementReference reference = new TradeSettlementReference();
        reference.setTradeSn(91L);
        reference.setTradeTypeCode("TRDC0001");
        reference.setBidSn(501L);
        when(tradeMapper.findSettlementReferenceByTradeId(91L)).thenReturn(reference);

        TradeSettlementReference result = reader.getByTradeSn(91L);

        assertThat(result).isSameAs(reference);
        assertThat(result.getTradeTypeCode()).isEqualTo("TRDC0001");
        assertThat(result.getBidSn()).isEqualTo(501L);
        verify(tradeMapper).findSettlementReferenceByTradeId(91L);
    }

    @Test
    void preservesNullBidReferenceForServiceTrade() {
        TradeSettlementReference reference = new TradeSettlementReference();
        reference.setTradeSn(92L);
        reference.setTradeTypeCode("TRDC0002");
        when(tradeMapper.findSettlementReferenceByTradeId(92L)).thenReturn(reference);

        TradeSettlementReference result = reader.getByTradeSn(92L);

        assertThat(result.getBidSn()).isNull();
        assertThat(result.getTradeTypeCode()).isEqualTo("TRDC0002");
    }

    @Test
    void rejectsInvalidTradeIdBeforeQueryingMapper() {
        assertThatThrownBy(() -> reader.getByTradeSn(0L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(tradeMapper);
    }

    @Test
    void rejectsMissingTradeReference() {
        when(tradeMapper.findSettlementReferenceByTradeId(91L)).thenReturn(null);

        assertThatThrownBy(() -> reader.getByTradeSn(91L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
