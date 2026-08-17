package nct.point;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.abuse.port.ActiveReportedUserReader;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.point.domain.PointBalance;
import nct.point.domain.PointLedger;
import nct.point.exception.PointException;
import nct.point.mapper.PointMapper;
import nct.point.mapper.SystemSettingMapper;
import nct.point.service.PointService;

/** 담당자 7 · F-PAY-010/F-PAY-012: 활성 신고 피신고자의 자발적 포인트 이동 제한 테스트입니다. */
@ExtendWith(MockitoExtension.class)
class PointServiceActiveReportRestrictionTest {

    private static final long USER_SN = 25L;

    @InjectMocks
    private PointService pointService;

    @Mock
    private PointMapper pointMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @Mock
    private ActiveReportedUserReader activeReportedUserReader;

    @Mock
    private AuditLogPort auditLogPort;

    @Test
    @DisplayName("활성 신고의 피신고자는 정산가능 포인트 전환 전에 차단된다")
    void blocksConversionBeforeLedgerMutation() {
        when(activeReportedUserReader.hasActiveReportAgainst(USER_SN)).thenReturn(true);

        assertThatThrownBy(() -> pointService.convertSettleableToAvailable(USER_SN, 10_000))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_CONVERT_EXCHANGE_BLOCKED_BY_ACTIVE_REPORT);

        InOrder order = inOrder(pointMapper, activeReportedUserReader);
        order.verify(pointMapper).lockUser(USER_SN);
        order.verify(activeReportedUserReader).hasActiveReportAgainst(USER_SN);
        verifyNoMoreInteractions(pointMapper);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("활성 신고의 피신고자는 사용가능 잔액만 쓰는 환전도 차감 전에 차단된다")
    void blocksExchangeBeforeLedgerMutation() {
        when(activeReportedUserReader.hasActiveReportAgainst(USER_SN)).thenReturn(true);

        assertThatThrownBy(() -> pointService.debitExchange(USER_SN, 10_000, "환전 신청 차감"))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_CONVERT_EXCHANGE_BLOCKED_BY_ACTIVE_REPORT);

        InOrder order = inOrder(pointMapper, activeReportedUserReader);
        order.verify(pointMapper).lockUser(USER_SN);
        order.verify(activeReportedUserReader).hasActiveReportAgainst(USER_SN);
        verifyNoMoreInteractions(pointMapper);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("활성 신고가 없으면 기존 전환 검증 흐름으로 진행한다")
    void continuesExistingFlowWhenNoActiveReportExists() {
        when(activeReportedUserReader.hasActiveReportAgainst(USER_SN)).thenReturn(false);
        PointBalance balance = new PointBalance();
        balance.setSettleableAmt(10_000);
        when(pointMapper.selectBalance(USER_SN)).thenReturn(balance);
        doAnswer(invocation -> {
            invocation.<PointLedger>getArgument(0).setPtLdgSn(1L);
            return 1;
        }).when(pointMapper).insertLedger(org.mockito.ArgumentMatchers.any());

        pointService.convertSettleableToAvailable(USER_SN, 10_000);

        InOrder order = inOrder(pointMapper, activeReportedUserReader);
        order.verify(pointMapper).lockUser(USER_SN);
        order.verify(activeReportedUserReader).hasActiveReportAgainst(USER_SN);
        verify(pointMapper).countActiveDisputes(USER_SN);
        verify(pointMapper, times(2)).insertLedger(org.mockito.ArgumentMatchers.any());
        verify(notificationService).notifyPointConvert(USER_SN, 10_000);
    }
}
