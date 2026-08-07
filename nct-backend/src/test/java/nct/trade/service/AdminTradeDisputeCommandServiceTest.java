package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.trade.dto.AdminTradeDisputeDecisionTarget;
import nct.trade.mapper.AdminTradeDisputeCommandMapper;

/** 담당자 7 · F-OPS-006: 거래·분쟁 조건부 상태전이와 직전 상태 복구를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AdminTradeDisputeCommandServiceTest {

    @Mock private AdminTradeDisputeCommandMapper mapper;

    private AdminTradeDisputeCommandService service;

    @BeforeEach
    void setUp() {
        service = new AdminTradeDisputeCommandService(mapper);
    }

    @Test
    void restoresPreviousTradeStatusAndClosesDispute() {
        AdminTradeDisputeDecisionTarget target = openTarget("TRDC0005");
        when(mapper.updateTradeStatus(25L, "TRDC0007", "TRDC0005", "99")).thenReturn(1);
        when(mapper.insertTradeStatusHistory(25L, "TRDC0005", "조정 완료", "99")).thenReturn(1);
        when(mapper.updateDisputeDecision(
                11L, "TRDC0021", "TRDC0018", "조정 완료", 99L, "99"))
                .thenReturn(1);

        service.restoreAndClose(target, "TRDC0021", "TRDC0018", "조정 완료", 99L);

        verify(mapper).updateTradeStatus(25L, "TRDC0007", "TRDC0005", "99");
        verify(mapper).updateDisputeDecision(
                11L, "TRDC0021", "TRDC0018", "조정 완료", 99L, "99");
    }

    @Test
    void rejectsLegacyDisputeWithoutRestorablePreviousStatus() {
        AdminTradeDisputeDecisionTarget target = openTarget(null);

        assertThatThrownBy(() -> service.restoreAndClose(
                target, "TRDC0021", "TRDC0018", "조정 완료", 99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("접수 전 거래 상태");

        verify(mapper, never()).updateTradeStatus(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsTradeHeldWhenDecisionIsHold() {
        AdminTradeDisputeDecisionTarget target = openTarget("TRDC0003");
        when(mapper.updateDisputeDecision(
                11L, "TRDC0023", "TRDC0017", "추가 확인", 99L, "99"))
                .thenReturn(1);

        service.keepOnHold(target, "TRDC0023", "추가 확인", 99L);

        verify(mapper, never()).updateTradeStatus(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(mapper).updateDisputeDecision(
                11L, "TRDC0023", "TRDC0017", "추가 확인", 99L, "99");
    }

    private AdminTradeDisputeDecisionTarget openTarget(String previousTradeStatus) {
        AdminTradeDisputeDecisionTarget target = new AdminTradeDisputeDecisionTarget();
        target.setDisputeSn(11L);
        target.setTradeSn(25L);
        target.setDisputeStatusCode("TRDC0016");
        target.setTradeStatusCode("TRDC0007");
        target.setPreviousTradeStatusCode(previousTradeStatus);
        return target;
    }
}
