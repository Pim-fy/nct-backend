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
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.TradeDisputeTargetReaderService;

class TradeDisputeTargetReaderTest {

    private TradeMapper tradeMapper;
    private TradeDisputeTargetReaderService reader;

    @BeforeEach
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        reader = new TradeDisputeTargetReaderService(tradeMapper);
    }

    @Test
    void returnsLockedServiceTradeParticipantsAndStatus() {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(91L);
        target.setRequesterUserId(10L);
        target.setProviderUserId(20L);
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0005");
        when(tradeMapper.findTradeDisputeTargetForUpdate(91L)).thenReturn(target);

        TradeDisputeTarget result = reader.lockByTradeSn(91L);

        assertThat(result).isSameAs(target);
        assertThat(result.getRequesterUserId()).isEqualTo(10L);
        assertThat(result.getProviderUserId()).isEqualTo(20L);
        assertThat(result.getTradeStatusCode()).isEqualTo("TRDC0005");
        verify(tradeMapper).findTradeDisputeTargetForUpdate(91L);
    }

    @Test
    void rejectsInvalidTradeIdBeforeQueryingMapper() {
        assertThatThrownBy(() -> reader.lockByTradeSn(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(tradeMapper);
    }

    @Test
    void rejectsMissingLockedTrade() {
        when(tradeMapper.findTradeDisputeTargetForUpdate(91L)).thenReturn(null);

        assertThatThrownBy(() -> reader.lockByTradeSn(91L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
