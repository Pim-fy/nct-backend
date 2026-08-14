package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.servicerequest.dto.ServiceRequestSanctionTarget;
import nct.servicerequest.mapper.ServiceRequestMapper;

/** 담당자 7 · F-OPS-007: 신고된 견적 요청의 보류 시간 보존과 복구 계약을 검증합니다. */
class ServiceRequestReportTargetHoldServiceTest {

    private ServiceRequestMapper serviceRequestMapper;
    private ReferenceDataService referenceDataService;
    private ServiceRequestReportTargetHoldService service;

    @BeforeEach
    void setUp() {
        serviceRequestMapper = mock(ServiceRequestMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new ServiceRequestReportTargetHoldService(
                serviceRequestMapper,
                referenceDataService);
    }

    @Test
    void pausesOpenRequestAndPreservesRemainingDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 0);
        ServiceRequestSanctionTarget target = new ServiceRequestSanctionTarget();
        target.setServiceRequestId(91L);
        target.setStatusCode("SVCC0002");
        target.setEffectiveDeadlineAt(now.plusHours(3));
        target.setDatabaseNow(now);
        when(serviceRequestMapper.findReportHoldTargetForUpdate(91L)).thenReturn(target);
        when(serviceRequestMapper.pauseServiceRequestForSanction(91L, "SVCC0002", "10"))
                .thenReturn(1);

        ReportTargetHoldResult result = service.pause(91L, "10");

        assertThat(result.changed()).isTrue();
        assertThat(result.previousStatusCode()).isEqualTo("SVCC0002");
        assertThat(result.remainingSeconds()).isEqualTo(10800L);
        verify(referenceDataService).requireActiveCode("SVCG01", "SVCC0005");
        verify(serviceRequestMapper).pauseServiceRequestForSanction(91L, "SVCC0002", "10");
    }

    @Test
    void restoresRequestWithPreservedRemainingDeadline() {
        ReportTargetRestoreCommand command = new ReportTargetRestoreCommand(
                91L, "SVCC0002", null, 10800L, "10");
        when(serviceRequestMapper.restoreServiceRequestAfterSanction(
                91L, "SVCC0002", 10800L, "10"))
                .thenReturn(1);

        assertThat(service.restore(command)).isTrue();

        verify(referenceDataService).requireActiveCode("SVCG01", "SVCC0002");
        verify(serviceRequestMapper).restoreServiceRequestAfterSanction(
                91L, "SVCC0002", 10800L, "10");
    }
}
