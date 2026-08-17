package nct.ops.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.abuse.mapper.AbuseReportMapper;
import nct.global.exception.CustomException;
import nct.member.mapper.MemberMapper;
import nct.member.port.MemberStatusChangeResult;
import nct.member.port.MemberStatusCommandPort;
import nct.member.port.AdminMemberIdentityReader;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.member.port.AccountRestrictionRecoveryPort;
import nct.ops.member.port.AccountSanctionPort;
import nct.trade.port.MemberTradeRestrictionPort;
import nct.trade.port.MemberTradeRestrictionResult;

/** 담당자 7 · F-OPS-019/020: 제재 계약 선검사·상태 멱등·오케스트레이션 순서를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock private MemberMapper memberMapper;
    @Mock private MemberStatusCommandPort memberStatusCommandPort;
    @Mock private AbuseReportMapper abuseReportMapper;
    @Mock private ObjectProvider<AccountSanctionPort> sanctionProvider;
    @Mock private ObjectProvider<AccountRestrictionRecoveryPort> recoveryProvider;
    @Mock private AccountSanctionPort sanctionPort;
    @Mock private MemberTradeRestrictionPort tradeRestrictionPort;
    @Mock private AuditLogPort auditLogPort;
    @Mock private NotificationService notificationService;
    @Mock private AdminMemberIdentityReader memberIdentityReader;

    private AdminMemberService service;

    @BeforeEach
    void setUp() {
        service = new AdminMemberService(
                memberMapper,
                memberStatusCommandPort,
                abuseReportMapper,
                sanctionProvider,
                recoveryProvider,
                tradeRestrictionPort,
                auditLogPort,
                notificationService,
                memberIdentityReader);
    }

    @Test
    void sanctionContractMissingFailsBeforeAnyMutation() {
        when(sanctionProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.changeStatus(
                10L, "USRC0002", "운영 제한", "request-1", 99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("제재 생성·해제 계약");

        verify(memberStatusCommandPort, never()).changeStatus(any());
        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
    }

    @Test
    void restrictionRunsMemberThenSanctionThenTradesThenAudit() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0001", "USRC0002", true));
        when(tradeRestrictionPort.restrictActiveTrades(any()))
                .thenReturn(new MemberTradeRestrictionResult(List.of(), 0));

        var result = service.changeStatus(
                10L, "USRC0002", "운영 제한", "request-1", 99L);

        assertThat(result.changed()).isTrue();
        assertThat(result.currentStatusCode()).isEqualTo("USRC0002");
        InOrder order = inOrder(
                memberStatusCommandPort, sanctionPort, tradeRestrictionPort, auditLogPort);
        order.verify(memberStatusCommandPort).changeStatus(any());
        order.verify(sanctionPort).restrict(any());
        order.verify(tradeRestrictionPort).restrictActiveTrades(any());
        order.verify(auditLogPort).record(any());
        verify(notificationService, times(1))
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sameStatusIsIdempotentWithoutDuplicateSideEffects() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0002", "USRC0002", false));

        service.changeStatus(10L, "USRC0002", "첫 재시도", "request-2", 99L);
        var result = service.changeStatus(
                10L, "USRC0002", "다른 요청 재시도", "request-3", 99L);

        assertThat(result.changed()).isFalse();
        verify(sanctionPort, never()).restrict(any());
        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never()).notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: 이미 활성인 회원의 같은 요청ID 재요청은 감사·알림을 만들지 않습니다. */
    @Test
    void activeToActiveWithSameRequestIdHasNoSideEffects() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0001", "USRC0001", false));

        service.changeStatus(
                10L, "USRC0001", "활성 재요청", "request-active", 99L);
        var result = service.changeStatus(
                10L, "USRC0001", "활성 재요청", "request-active", 99L);

        assertThat(result.changed()).isFalse();
        verify(sanctionPort, times(2)).release(any());
        verify(memberStatusCommandPort, times(2)).changeStatus(any());
        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never())
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: requestId가 달라도 실질 상태가 같으면 감사·알림을 만들지 않습니다. */
    @Test
    void activeToActiveWithDifferentRequestIdHasNoSideEffects() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0001", "USRC0001", false));

        service.changeStatus(10L, "USRC0001", "첫 재요청", "request-active-1", 99L);
        var result = service.changeStatus(
                10L, "USRC0001", "두 번째 재요청", "request-active-2", 99L);

        assertThat(result.changed()).isFalse();
        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never())
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: 실제 정지 요청을 같은 requestId로 재시도해도 한 번만 반영합니다. */
    @Test
    void activeToSuspendedSameRequestIdIsAppliedOnce() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(
                        new MemberStatusChangeResult("USRC0001", "USRC0002", true),
                        new MemberStatusChangeResult("USRC0002", "USRC0002", false));
        when(tradeRestrictionPort.restrictActiveTrades(any()))
                .thenReturn(new MemberTradeRestrictionResult(List.of(), 0));

        var first = service.changeStatus(
                10L, "USRC0002", "운영 제한", "request-suspend", 99L);
        var retry = service.changeStatus(
                10L, "USRC0002", "운영 제한", "request-suspend", 99L);

        assertThat(first.changed()).isTrue();
        assertThat(retry.changed()).isFalse();
        verify(sanctionPort, times(1)).restrict(any());
        verify(tradeRestrictionPort, times(1)).restrictActiveTrades(any());
        verify(auditLogPort, times(1)).record(any());
        verify(notificationService, times(1))
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: 실제 정지 해제는 감사·알림을 한 번씩 생성합니다. */
    @Test
    void suspendedToActiveSameRequestIdIsAppliedOnce() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(sanctionPort.release(any())).thenReturn(true, false);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(
                        new MemberStatusChangeResult("USRC0002", "USRC0001", true),
                        new MemberStatusChangeResult("USRC0001", "USRC0001", false));
        AccountRestrictionRecoveryPort recoveryPort =
                org.mockito.Mockito.mock(AccountRestrictionRecoveryPort.class);
        when(recoveryProvider.getIfAvailable()).thenReturn(recoveryPort);

        var first = service.changeStatus(
                10L, "USRC0001", "제한 해제", "request-release", 99L);
        var retry = service.changeStatus(
                10L, "USRC0001", "제한 해제", "request-release", 99L);

        assertThat(first.changed()).isTrue();
        assertThat(retry.changed()).isFalse();
        verify(recoveryPort, times(1)).restorePending(10L, 99L, "제한 해제");
        verify(auditLogPort, times(1)).record(any());
        verify(notificationService, times(1))
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: 다른 활성 제재가 남아도 실제 수동 제재 해제 감사는 누락하지 않습니다. */
    @Test
    void releasedManualSanctionIsAuditedWhenAnotherSuspensionRemains() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(sanctionPort.release(any())).thenReturn(true);
        when(sanctionPort.hasActiveSuspension(10L)).thenReturn(true);

        var result = service.changeStatus(
                10L, "USRC0001", "수동 제재 해제", "request-partial-release", 99L);

        assertThat(result.changed()).isTrue();
        assertThat(result.currentStatusCode()).isEqualTo("USRC0002");
        verify(memberStatusCommandPort, never()).changeStatus(any());
        verify(auditLogPort, times(1)).record(any());
        verify(notificationService, never())
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sanctionFailureStopsFollowingWritesSoOuterTransactionCanRollBack() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0001", "USRC0002", true));
        doThrow(new IllegalStateException("sanction insert failed"))
                .when(sanctionPort).restrict(any());

        assertThatThrownBy(() -> service.changeStatus(
                10L, "USRC0002", "운영 제한", "request-rollback", 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sanction insert failed");

        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never())
                .notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }
}
