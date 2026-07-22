package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.ops.operation.port.SellerCancellationDecisionPort;

/** 담당자 7 · F-OPS-004/F-OPS-015: 거래 판정과 감사 기록 계약을 함께 검증합니다. */
class AdminSellerCancellationServiceTest {

    private SellerCancellationDecisionPort sellerCancellationDecisionPort;
    private AuditLogPort auditLogPort;
    private AdminSellerCancellationService service;

    @BeforeEach
    void setUp() {
        sellerCancellationDecisionPort = mock(SellerCancellationDecisionPort.class);
        auditLogPort = mock(AuditLogPort.class);
        service = new AdminSellerCancellationService(sellerCancellationDecisionPort, auditLogPort);
    }

    @Test
    void approvesThroughTradePortAndRecordsAudit() {
        service.decide(91L, SellerCancellationDecision.APPROVED, " approved by admin ", 7L);

        ArgumentCaptor<SellerCancellationDecisionCommand> decisionCaptor =
                ArgumentCaptor.forClass(SellerCancellationDecisionCommand.class);
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        InOrder inOrder = inOrder(sellerCancellationDecisionPort, auditLogPort);

        inOrder.verify(sellerCancellationDecisionPort).decide(decisionCaptor.capture());
        inOrder.verify(auditLogPort).record(auditCaptor.capture());

        assertThat(decisionCaptor.getValue().tradeSn()).isEqualTo(91L);
        assertThat(decisionCaptor.getValue().decision()).isEqualTo(SellerCancellationDecision.APPROVED);
        assertThat(decisionCaptor.getValue().reason()).isEqualTo("approved by admin");
        assertThat(decisionCaptor.getValue().adminId()).isEqualTo("7");
        assertThat(decisionCaptor.getValue().requestId()).startsWith("seller-cancel:");

        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_APPROVE");
        assertThat(auditCaptor.getValue().actorId()).isEqualTo("7");
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo("TRADE");
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(91L);
        assertThat(auditCaptor.getValue().reason()).isEqualTo("approved by admin");
        assertThat(auditCaptor.getValue().afterSummary()).isEqualTo("sellerCancellationDecision=APPROVED");
        assertThat(auditCaptor.getValue().requestId()).isEqualTo(decisionCaptor.getValue().requestId());
    }

    @Test
    void rejectsThroughTradePortAndRecordsAudit() {
        service.decide(91L, SellerCancellationDecision.REJECTED, " insufficient evidence ", 7L);

        ArgumentCaptor<SellerCancellationDecisionCommand> decisionCaptor =
                ArgumentCaptor.forClass(SellerCancellationDecisionCommand.class);
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        InOrder inOrder = inOrder(sellerCancellationDecisionPort, auditLogPort);

        inOrder.verify(sellerCancellationDecisionPort).decide(decisionCaptor.capture());
        inOrder.verify(auditLogPort).record(auditCaptor.capture());

        assertThat(decisionCaptor.getValue().decision()).isEqualTo(SellerCancellationDecision.REJECTED);
        assertThat(decisionCaptor.getValue().reason()).isEqualTo("insufficient evidence");
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_REJECT");
        assertThat(auditCaptor.getValue().reason()).isEqualTo("insufficient evidence");
        assertThat(auditCaptor.getValue().afterSummary()).isEqualTo("sellerCancellationDecision=REJECTED");
    }
}
