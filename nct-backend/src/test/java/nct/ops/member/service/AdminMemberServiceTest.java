package nct.ops.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
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
    @Mock private AccountSanctionPort sanctionPort;
    @Mock private MemberTradeRestrictionPort tradeRestrictionPort;
    @Mock private AuditLogPort auditLogPort;
    @Mock private NotificationService notificationService;

    private AdminMemberService service;

    @BeforeEach
    void setUp() {
        service = new AdminMemberService(
                memberMapper,
                memberStatusCommandPort,
                abuseReportMapper,
                sanctionProvider,
                tradeRestrictionPort,
                auditLogPort,
                notificationService);
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
    }

    @Test
    void sameStatusIsIdempotentWithoutDuplicateSideEffects() {
        when(sanctionProvider.getIfAvailable()).thenReturn(sanctionPort);
        when(memberStatusCommandPort.changeStatus(any()))
                .thenReturn(new MemberStatusChangeResult("USRC0002", "USRC0002", false));

        var result = service.changeStatus(
                10L, "USRC0002", "재시도", "request-2", 99L);

        assertThat(result.changed()).isFalse();
        verify(sanctionPort, never()).restrict(any());
        verify(tradeRestrictionPort, never()).restrictActiveTrades(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never()).notify(any(Long.class), any(), any(), any(), any(), any(), any());
    }
}
