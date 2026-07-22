package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;

/** 담당자 7 · F-OPS-007: 신고 처리 계약에 관리자·사유·결정값을 전달하는지 검증합니다. */
class AdminReportOperationServiceTest {

    private AdminReportDecisionPort adminReportDecisionPort;
    private AdminReportOperationService service;

    @BeforeEach
    void setUp() {
        adminReportDecisionPort = mock(AdminReportDecisionPort.class);
        service = new AdminReportOperationService(adminReportDecisionPort);
    }

    @Test
    void forwardsProcessedDecisionWithNormalizedReason() {
        service.decide(91L, AdminReportDecision.PROCESSED, " confirmed by admin ", 7L);

        ArgumentCaptor<AdminReportDecisionCommand> commandCaptor =
                ArgumentCaptor.forClass(AdminReportDecisionCommand.class);
        verify(adminReportDecisionPort).decide(commandCaptor.capture());

        AdminReportDecisionCommand command = commandCaptor.getValue();
        assertThat(command.reportSn()).isEqualTo(91L);
        assertThat(command.decision()).isEqualTo(AdminReportDecision.PROCESSED);
        assertThat(command.reason()).isEqualTo("confirmed by admin");
        assertThat(command.adminId()).isEqualTo("7");
        assertThat(command.requestId()).startsWith("admin-report:");
    }
}
