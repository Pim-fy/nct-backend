package nct.ops.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.common.domain.RefType;
import nct.ops.risk.event.ReportCreatedRiskSignal;
import nct.ops.risk.port.ReportRiskSignalReader;
import nct.ops.risk.port.RiskDetectionPolicy;
import nct.ops.risk.port.RiskDetectionPolicyReader;
import nct.ops.risk.port.SettlementRiskCandidate;
import nct.ops.risk.port.SettlementRiskSignalReader;

class RiskDetectionServiceTest {

    private RiskDetectionPolicyReader policyReader;
    private ReportRiskSignalReader reportSignalReader;
    private SettlementRiskSignalReader settlementSignalReader;
    private RiskEventService riskEventService;
    private RiskDetectionService service;

    @BeforeEach
    void setUp() {
        policyReader = mock(RiskDetectionPolicyReader.class);
        reportSignalReader = mock(ReportRiskSignalReader.class);
        settlementSignalReader = mock(SettlementRiskSignalReader.class);
        riskEventService = mock(RiskEventService.class);
        service = new RiskDetectionService(
                policyReader, reportSignalReader, settlementSignalReader, riskEventService);
        when(policyReader.getPolicy()).thenReturn(policy());
        when(riskEventService.recordOnceSince(any(), any()))
                .thenReturn(new RiskEventResult(1L, true));
    }

    @Test
    void createsTradeSurgeAndRepeatedReportEventsAtThreshold() {
        when(reportSignalReader.countTradeReportsSince(any())).thenReturn(10L);
        when(reportSignalReader.countDistinctReportersForTargetSince(
                org.mockito.ArgumentMatchers.eq(55L), any())).thenReturn(3L);

        service.evaluateReportSignals(new ReportCreatedRiskSignal(9L, 55L, true));

        ArgumentCaptor<RiskEventCommand> captor = ArgumentCaptor.forClass(RiskEventCommand.class);
        verify(riskEventService, org.mockito.Mockito.times(2))
                .recordOnceSince(captor.capture(), any());
        assertThat(captor.getAllValues()).extracting(RiskEventCommand::typeCode)
                .containsExactly("RSKC0005", "RSKC0007");
        assertThat(captor.getAllValues().get(1).referenceTypeCode())
                .isEqualTo(RefType.MEMBER.getCode());
        assertThat(captor.getAllValues().get(1).referenceSn()).isEqualTo(55L);
    }

    @Test
    void doesNotCreateReportEventsBelowThreshold() {
        when(reportSignalReader.countTradeReportsSince(any())).thenReturn(9L);
        when(reportSignalReader.countDistinctReportersForTargetSince(
                org.mockito.ArgumentMatchers.eq(55L), any())).thenReturn(2L);

        service.evaluateReportSignals(new ReportCreatedRiskSignal(9L, 55L, true));

        verify(riskEventService, never()).recordOnceSince(any(), any());
    }

    @Test
    void createsLongHeldSettlementEventWithSettlementReference() {
        LocalDateTime holdStartedAt = LocalDateTime.now().minusDays(8);
        when(settlementSignalReader.findLongHeldSettlements(any(), org.mockito.ArgumentMatchers.eq(200)))
                .thenReturn(List.of(new SettlementRiskCandidate(81L, 91L, holdStartedAt)));

        int created = service.scanLongHeldSettlements();

        ArgumentCaptor<RiskEventCommand> captor = ArgumentCaptor.forClass(RiskEventCommand.class);
        verify(riskEventService).recordOnceSince(captor.capture(),
                org.mockito.ArgumentMatchers.eq(holdStartedAt));
        assertThat(created).isEqualTo(1);
        assertThat(captor.getValue().typeCode()).isEqualTo("RSKC0006");
        assertThat(captor.getValue().referenceTypeCode()).isEqualTo(RefType.SETTLEMENT.getCode());
        assertThat(captor.getValue().referenceSn()).isEqualTo(81L);
    }

    @Test
    void periodicScanRecoversTradeAndRepeatedReportSignals() {
        when(reportSignalReader.countTradeReportsSince(any())).thenReturn(10L);
        when(reportSignalReader.findRepeatedReportedUserIdsSince(
                any(), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(200)))
                .thenReturn(List.of(55L));

        int created = service.scanReportSignals();

        ArgumentCaptor<RiskEventCommand> captor = ArgumentCaptor.forClass(RiskEventCommand.class);
        verify(riskEventService, org.mockito.Mockito.times(2))
                .recordOnceSince(captor.capture(), any());
        assertThat(created).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(RiskEventCommand::typeCode)
                .containsExactly("RSKC0005", "RSKC0007");
    }

    private RiskDetectionPolicy policy() {
        return new RiskDetectionPolicy(10, 60, 7, 3, 7, 5, 10);
    }
}
