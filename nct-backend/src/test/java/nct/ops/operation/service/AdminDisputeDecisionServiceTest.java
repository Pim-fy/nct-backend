package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import nct.chat.service.ChatService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.operation.domain.AdminDisputeDecision;
import nct.ops.operation.domain.AdminDisputeDecisionCommittedEvent;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.trade.dto.AdminTradeDisputeDecisionTarget;
import nct.trade.port.AdminTradeDisputeCommandPort;

/** 담당자 7 · F-OPS-006: 관리자 판정의 전액 환불·멱등·복구 조립을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AdminDisputeDecisionServiceTest {

    @Mock private AdminTradeDisputeCommandPort disputeCommandPort;
    @Mock private SettlementService settlementService;
    @Mock private PointService pointService;
    @Mock private ChatService chatService;
    @Mock private AuditLogPort auditLogPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminDisputeDecisionService service;

    @BeforeEach
    void setUp() {
        service = new AdminDisputeDecisionService(
                disputeCommandPort,
                settlementService,
                pointService,
                chatService,
                auditLogPort,
                eventPublisher);
    }

    @Test
    void refundsServiceTradeEscrowToRequesterAndClosesSettlement() {
        AdminTradeDisputeDecisionTarget target = serviceTarget();
        when(disputeCommandPort.lockByReportSn(11L)).thenReturn(target);
        when(pointService.refundEscrow(
                32L, 25L, RefType.TRADE, 25L,
                "관리자 거래 신고 전액 환불: 전액 환불 확정"))
                .thenReturn(70_000L);

        service.decide(11L, AdminDisputeDecision.REFUND, "전액 환불 확정", 99L);
        verify(disputeCommandPort).cancelAndClose(
                target, "TRDC0012", "전액 환불 확정", 99L);
        verify(settlementService).closeRefundedByTradeIfOpen(25L, 99L);
        verify(pointService).refundEscrow(
                32L, 25L, RefType.TRADE, 25L,
                "관리자 거래 신고 전액 환불: 전액 환불 확정");
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().referenceTypeCode())
                .isEqualTo(RefType.ABUSE_REPORT.getCode());
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(11L);
        assertThat(auditCaptor.getValue().relatedReferenceTypeCode())
                .isEqualTo(RefType.TRADE.getCode());
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(25L);
        verify(eventPublisher).publishEvent(any(AdminDisputeDecisionCommittedEvent.class));
    }

    @Test
    void sameFinalDecisionIsIdempotentWithoutDuplicateSideEffects() {
        AdminTradeDisputeDecisionTarget target = serviceTarget();
        target.setReportStatusCode("ABSC0003");
        target.setTradeDecisionResultCode("TRDC0012");
        when(disputeCommandPort.lockByReportSn(11L)).thenReturn(target);

        service.decide(11L, AdminDisputeDecision.REFUND, "재요청", 99L);
        verify(disputeCommandPort, never()).cancelAndClose(any(), any(), any(), any());
        verify(settlementService, never()).closeRefundedByTradeIfOpen(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(pointService, never()).refundEscrow(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(),
                org.mockito.ArgumentMatchers.anyLong(),
                any());
        verify(auditLogPort, never()).record(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completeRestoresTradeAndResumesHeldSettlement() {
        AdminTradeDisputeDecisionTarget target = serviceTarget();
        when(disputeCommandPort.lockByReportSn(11L)).thenReturn(target);

        service.decide(11L, AdminDisputeDecision.COMPLETE, "당사자 조정 완료", 99L);
        verify(disputeCommandPort).restoreAndClose(
                target, "TRDC0011", "당사자 조정 완료", 99L);
        verify(settlementService).resumeByTradeIfOnHold(25L, 99L);
        verify(chatService).reopenTradeChatRoom(25L);
        verify(pointService, never()).refundEscrow(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(),
                org.mockito.ArgumentMatchers.anyLong(),
                any());
    }

    @Test
    void differentDecisionCannotOverwriteClosedDispute() {
        AdminTradeDisputeDecisionTarget target = serviceTarget();
        target.setReportStatusCode("ABSC0003");
        target.setTradeDecisionResultCode("TRDC0011");
        when(disputeCommandPort.lockByReportSn(11L)).thenReturn(target);

        assertThatThrownBy(() -> service.decide(
                11L, AdminDisputeDecision.REFUND, "결과 변경", 99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("다른 판정");
    }

    @Test
    void holdEnsuresSettlementHoldWithoutResolvedNotification() {
        AdminTradeDisputeDecisionTarget target = serviceTarget();
        when(disputeCommandPort.lockByReportSn(11L)).thenReturn(target);

        service.decide(11L, AdminDisputeDecision.HOLD, "추가 확인 중", 99L);

        verify(settlementService).holdUpByTradeIfPending(25L, "추가 확인 중");
        verify(disputeCommandPort).keepOnHold(
                target, "TRDC0013", "추가 확인 중", 99L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private AdminTradeDisputeDecisionTarget serviceTarget() {
        AdminTradeDisputeDecisionTarget target = new AdminTradeDisputeDecisionTarget();
        target.setReportSn(11L);
        target.setTradeSn(25L);
        target.setReportStatusCode("ABSC0001");
        target.setPreviousTradeStatusCode("TRDC0003");
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0007");
        target.setRequesterUserSn(32L);
        target.setProviderUserSn(33L);
        target.setSettlementHoldApplied(true);
        target.setChatClosed(true);
        return target;
    }
}
