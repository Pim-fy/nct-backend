package nct.ops.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventResult;
import nct.ops.risk.service.RiskEventService;
import nct.global.exception.CustomException;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;

class SensitiveDataInspectionServiceTest {

    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";

    private RiskEventService riskEventService;
    private SensitiveDetectionReportPort reportPort;
    private SensitiveDataInspectionService service;

    @BeforeEach
    void setUp() {
        riskEventService = mock(RiskEventService.class);
        reportPort = mock(SensitiveDetectionReportPort.class);
        service = new SensitiveDataInspectionService(
                new SensitiveDataMasker(), riskEventService, reportPort);
    }

    @Test
    void masksTextAndRecordsOnlySafeDetectionSummary() {
        when(riskEventService.recordOnce(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RiskEventResult(10L, true));
        when(reportPort.requestReport(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SensitiveDetectionReportResult(
                        SensitiveDetectionReportResult.Status.CREATED, 31L));

        SensitiveDataInspectionResult result = service.inspect(
                "contact me at user@example.com", REQUEST_ID,
                "REFC0004", 7L, "SYSTEM");

        assertThat(result.detected()).isTrue();
        assertThat(result.maskedText()).doesNotContain("user@example.com");
        var commandCaptor = forClass(RiskEventCommand.class);
        verify(riskEventService).recordOnce(commandCaptor.capture());
        assertThat(commandCaptor.getValue().content())
                .contains("request=")
                .doesNotContain("user@example.com", REQUEST_ID);

        // 신고 담당자에게도 원문은 보내지 않고 위험 이벤트 번호와 탐지 종류만 보낸다.
        var reportCaptor = forClass(SensitiveDetectionReportCommand.class);
        verify(reportPort).requestReport(reportCaptor.capture());
        assertThat(reportCaptor.getValue().riskEventSn()).isEqualTo(10L);
        assertThat(reportCaptor.getValue().toString()).doesNotContain("user@example.com", REQUEST_ID);
        assertThat(result.report().status())
                .isEqualTo(SensitiveDetectionReportResult.Status.CREATED);
    }

    @Test
    void doesNotCreateRiskEventWhenNothingIsDetected() {
        SensitiveDataInspectionResult result = service.inspect(
                "ordinary message", REQUEST_ID, "REFC0004", 7L, "SYSTEM");

        assertThat(result.detected()).isFalse();
        assertThat(result.riskEvent()).isNull();
        verify(riskEventService, never()).recordOnce(org.mockito.ArgumentMatchers.any());
        verify(reportPort, never()).requestReport(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPersonalOrUnboundedDetectionKey() {
        assertThatThrownBy(() -> service.inspect(
                "user@example.com", "user@example.com", "REFC0004", 7L, "SYSTEM"))
                .isInstanceOf(CustomException.class);
        verify(riskEventService, never()).recordOnce(org.mockito.ArgumentMatchers.any());
        verify(reportPort, never()).requestReport(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retriesReportConnectionWhenRiskEventWasAlreadyCreated() {
        // 앞선 신고 저장만 실패했을 수 있으므로 기존 RISK_EVENT를 재사용해도 포트를 호출한다.
        // 실제 신고 서비스는 riskEventSn 기준으로 새 신고 생성 또는 기존 신고 재사용을 판단한다.
        when(riskEventService.recordOnce(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RiskEventResult(10L, false));
        when(reportPort.requestReport(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SensitiveDetectionReportResult(
                        SensitiveDetectionReportResult.Status.REUSED, 31L));

        SensitiveDataInspectionResult result = service.inspect(
                "contact me at user@example.com", REQUEST_ID,
                "REFC0004", 7L, "SYSTEM");

        assertThat(result.riskEvent().created()).isFalse();
        assertThat(result.report().status())
                .isEqualTo(SensitiveDetectionReportResult.Status.REUSED);
        var reportCaptor = forClass(SensitiveDetectionReportCommand.class);
        verify(reportPort).requestReport(reportCaptor.capture());
        assertThat(reportCaptor.getValue().riskEventSn()).isEqualTo(10L);
    }
}
