package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.settlement.service.SettlementService;
import nct.trade.dto.MemberActiveTradeTarget;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.MemberTradeRestrictionCommand;

/** 담당자 7 · F-OPS-020: 실제 전이된 거래만 이력·정산 보류로 연결되는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class MemberTradeRestrictionServiceTest {

    @Mock private TradeMapper tradeMapper;
    @Mock private SettlementService settlementService;

    private MemberTradeRestrictionService service;

    @BeforeEach
    void setUp() {
        service = new MemberTradeRestrictionService(tradeMapper, settlementService);
    }

    @Test
    void restrictsActiveTradesAndHoldsPendingSettlement() {
        var target = new MemberActiveTradeTarget(41L, 10L, 20L, "TRDC0005");
        when(tradeMapper.findActiveTradesByMemberForUpdate(10L)).thenReturn(List.of(target));
        when(tradeMapper.holdTradeForMemberRestriction(41L, "TRDC0005", "99")).thenReturn(1);
        when(settlementService.holdUpByTradeIfPending(41L, "회원 계정 제한에 따른 거래 보류: 운영 제한"))
                .thenReturn(true);

        var result = service.restrictActiveTrades(
                new MemberTradeRestrictionCommand(10L, 99L, "운영 제한"));

        assertThat(result.restrictedTradeCount()).isEqualTo(1);
        assertThat(result.heldSettlementCount()).isEqualTo(1);
        assertThat(result.restrictedTrades().getFirst().counterpartUserSn()).isEqualTo(20L);

        InOrder order = inOrder(tradeMapper, settlementService);
        order.verify(tradeMapper).findActiveTradesByMemberForUpdate(10L);
        order.verify(tradeMapper).holdTradeForMemberRestriction(41L, "TRDC0005", "99");
        order.verify(tradeMapper).insertStatusHistory(
                41L, "TRDC0007", "회원 계정 제한에 따른 거래 보류: 운영 제한");
        order.verify(settlementService).holdUpByTradeIfPending(
                41L, "회원 계정 제한에 따른 거래 보류: 운영 제한");
    }
}
