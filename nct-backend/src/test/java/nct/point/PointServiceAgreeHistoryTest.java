package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.agree.domain.AgreeActType;
import nct.agree.domain.AgreeRef;
import nct.agree.domain.AgreeType;
import nct.agree.service.AgreeHistoryService;
import nct.common.domain.RefType;
import nct.notification.service.NotificationService;
import nct.point.domain.PointBalance;
import nct.point.domain.PointLedger;
import nct.point.mapper.PointMapper;
import nct.point.mapper.SystemSettingMapper;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-08-18)
 *
 * [테스트 - REQ-OPS-016] hold()/convertHoldToEscrow()가 동의 이력(AgreeHistoryService)을
 * 실제로 남기는지 검증한다. 공유 DB를 쓰지 않고 매퍼·동의 이력 서비스를 가짜로 주입한다.
 */
@ExtendWith(MockitoExtension.class)
class PointServiceAgreeHistoryTest {

    @InjectMocks PointService pointService;

    @Mock PointMapper pointMapper;
    @Mock NotificationService notificationService;
    @Mock SystemSettingMapper systemSettingMapper;
    @Mock AgreeHistoryService agreeHistoryService;

    /** insertLedger 호출 시 실제 DB의 useGeneratedKeys처럼 ptLdgSn을 채워 돌려준다 */
    private void stubGeneratedLedgerSn(long sn) {
        doAnswer(invocation -> {
            PointLedger row = invocation.getArgument(0);
            row.setPtLdgSn(sn);
            return null;
        }).when(pointMapper).insertLedger(any(PointLedger.class));
    }

    @Test
    @DisplayName("hold(): 홀딩 원장 행 sn으로 POINT_HOLD 동의 이력을 남긴다")
    void holdRecordsAgreeHistoryWithHoldLedgerRef() {
        long usrSn = 1L;
        stubGeneratedLedgerSn(999L);
        when(pointMapper.selectActiveHoldAmtByRef(usrSn, RefType.BID.getCode(), 10L)).thenReturn(0L);
        when(pointMapper.selectBalance(usrSn)).thenReturn(balance(100_000L, 0L, 0L));

        pointService.hold(usrSn, 10_000L, RefType.BID, 10L, "입찰 홀딩");

        ArgumentCaptor<AgreeRef> refCaptor = ArgumentCaptor.forClass(AgreeRef.class);
        verify(agreeHistoryService).record(eq(usrSn), eq(AgreeType.TERMS_OF_SERVICE),
                eq(AgreeActType.POINT_HOLD), eq(true), refCaptor.capture());
        assertThat(refCaptor.getValue().getPtLdgSn()).isEqualTo(999L);
    }

    @Test
    @DisplayName("convertHoldToEscrow(): 보관금전환 원장 행 sn으로 POINT_HOLD 동의 이력을 남긴다")
    void convertHoldToEscrowRecordsAgreeHistoryWithEscrowLedgerRef() {
        long usrSn = 2L;
        stubGeneratedLedgerSn(888L);
        when(pointMapper.selectActiveHoldAmtByRef(usrSn, RefType.BID.getCode(), 20L)).thenReturn(50_000L);
        when(pointMapper.selectBalance(usrSn)).thenReturn(balance(0L, 50_000L, 0L));

        pointService.convertHoldToEscrow(usrSn, RefType.BID, 20L, "낙찰 보관금 전환");

        ArgumentCaptor<AgreeRef> refCaptor = ArgumentCaptor.forClass(AgreeRef.class);
        verify(agreeHistoryService).record(eq(usrSn), eq(AgreeType.TERMS_OF_SERVICE),
                eq(AgreeActType.POINT_HOLD), eq(true), refCaptor.capture());
        assertThat(refCaptor.getValue().getPtLdgSn()).isEqualTo(888L);
    }

    private static PointBalance balance(long available, long hold, long settleable) {
        PointBalance bal = new PointBalance();
        bal.setAvailableAmt(available);
        bal.setHoldAmt(hold);
        bal.setSettleableAmt(settleable);
        return bal;
    }
}
