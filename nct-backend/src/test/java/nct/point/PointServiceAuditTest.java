package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.abuse.port.ActiveReportedUserReader;
import nct.common.domain.RefType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.point.domain.PointBalance;
import nct.point.domain.PointLedger;
import nct.point.exception.DuplicateHoldException;
import nct.point.mapper.PointMapper;
import nct.point.mapper.SystemSettingMapper;
import nct.point.service.PointService;

/** 담당자 7 · F-OPS-015: 포인트 원장 변동의 공통 감사 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class PointServiceAuditTest {

    @InjectMocks
    private PointService pointService;

    @Mock private PointMapper pointMapper;
    @Mock private NotificationService notificationService;
    @Mock private SystemSettingMapper systemSettingMapper;
    @Mock private ActiveReportedUserReader activeReportedUserReader;
    @Mock private AuditLogPort auditLogPort;

    @Test
    @DisplayName("포인트 홀딩은 원장 행별 감사로그를 남기되 원래 사유 원문을 전달하지 않는다")
    void holdRecordsControlledAuditSummaryWithoutOriginalReason() {
        long userSn = 25L;
        long bidSn = 91L;
        PointBalance balance = new PointBalance();
        balance.setAvailableAmt(50_000);
        balance.setHoldAmt(0);
        when(pointMapper.lockUser(userSn)).thenReturn(userSn);
        when(pointMapper.selectActiveHoldAmtByRef(userSn, RefType.BID.getCode(), bidSn)).thenReturn(0L);
        when(pointMapper.selectBalance(userSn)).thenReturn(balance);
        AtomicLong sequence = new AtomicLong(100L);
        doAnswer(invocation -> {
            invocation.<PointLedger>getArgument(0).setPtLdgSn(sequence.getAndIncrement());
            return 1;
        }).when(pointMapper).insertLedger(any(PointLedger.class));

        pointService.hold(
                userSn,
                12_000,
                RefType.BID,
                bidSn,
                "문의자 연락처 010-1234-5678");

        ArgumentCaptor<AuditLogCommand> captor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort, org.mockito.Mockito.times(2)).record(captor.capture());
        List<AuditLogCommand> commands = captor.getAllValues();
        assertThat(commands).extracting(AuditLogCommand::referenceSn).containsExactly(100L, 101L);
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.actionCode()).isEqualTo("CREATE");
            assertThat(command.actorId()).isEqualTo("25");
            assertThat(command.referenceTypeCode()).isEqualTo(RefType.POINT_LEDGER.getCode());
            assertThat(command.reason()).isEqualTo("포인트 원장 변동");
            assertThat(command.beforeSummary()).doesNotContain("010-1234-5678", "문의자");
            assertThat(command.afterSummary()).doesNotContain("010-1234-5678", "문의자");
            assertThat(command.requestId()).startsWith("point-ledger:");
            assertThat(command.relatedReferenceTypeCode()).isEqualTo(RefType.BID.getCode());
            assertThat(command.relatedReferenceSn()).isEqualTo(bidSn);
        });
    }

    /** 담당자 7 · F-OPS-015: 중복 홀딩은 새 원장과 감사로그를 만들지 않습니다. */
    @Test
    @DisplayName("동일 참조 포인트 홀딩 재요청은 감사로그를 중복 생성하지 않는다")
    void repeatedHoldDoesNotRecordAnotherAudit() {
        long userSn = 25L;
        long bidSn = 91L;
        PointBalance balance = new PointBalance();
        balance.setAvailableAmt(50_000);
        balance.setHoldAmt(0);
        when(pointMapper.lockUser(userSn)).thenReturn(userSn);
        when(pointMapper.selectActiveHoldAmtByRef(userSn, RefType.BID.getCode(), bidSn))
                .thenReturn(0L, 12_000L);
        when(pointMapper.selectBalance(userSn)).thenReturn(balance);
        AtomicLong sequence = new AtomicLong(100L);
        doAnswer(invocation -> {
            invocation.<PointLedger>getArgument(0).setPtLdgSn(sequence.getAndIncrement());
            return 1;
        }).when(pointMapper).insertLedger(any(PointLedger.class));

        pointService.hold(userSn, 12_000, RefType.BID, bidSn, "입찰 포인트 홀딩");

        assertThatThrownBy(() -> pointService.hold(
                userSn,
                12_000,
                RefType.BID,
                bidSn,
                "입찰 포인트 홀딩"))
                .isInstanceOf(DuplicateHoldException.class);

        verify(auditLogPort, org.mockito.Mockito.times(2)).record(any(AuditLogCommand.class));
    }

    /** 담당자 7 · F-OPS-015: 자동 보정은 포인트 소유자가 아닌 시스템 행위로 기록합니다. */
    @Test
    @DisplayName("자동 포인트 보정은 시스템 행위자로 기록한다")
    void automaticAdjustmentRecordsSystemActor() {
        long userSn = 25L;
        PointBalance balance = new PointBalance();
        balance.setAvailableAmt(50_000);
        when(pointMapper.lockUser(userSn)).thenReturn(userSn);
        when(pointMapper.selectBalance(userSn)).thenReturn(balance);
        doAnswer(invocation -> {
            invocation.<PointLedger>getArgument(0).setPtLdgSn(100L);
            return 1;
        }).when(pointMapper).insertLedger(any(PointLedger.class));

        pointService.reverseCharge(userSn, 12_000, "PG 승인 실패 자동 보정");

        ArgumentCaptor<AuditLogCommand> captor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().referenceTypeCode()).isEqualTo(RefType.POINT_LEDGER.getCode());
        assertThat(captor.getValue().reason()).isEqualTo("포인트 원장 변동");
        assertThat(captor.getValue().beforeSummary()).doesNotContain("PG 승인 실패 자동 보정");
        assertThat(captor.getValue().afterSummary()).doesNotContain("PG 승인 실패 자동 보정");
    }
}
