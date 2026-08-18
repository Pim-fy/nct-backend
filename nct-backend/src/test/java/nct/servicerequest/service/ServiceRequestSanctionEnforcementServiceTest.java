package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.ops.reference.service.ReferenceDataService;
import nct.servicerequest.dto.ServiceRequestSanctionTarget;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.servicerequest.port.MemberServiceRequestEnforcementCommand;

/** 담당자 7 · F-OPS-007/F-SVC-003: 영구 제재 취소와 운영보류 상태를 구분하는 회귀 테스트. */
@ExtendWith(MockitoExtension.class)
class ServiceRequestSanctionEnforcementServiceTest {

    @Mock
    private ServiceRequestMapper serviceRequestMapper;
    @Mock
    private ReferenceDataService referenceDataService;

    private ServiceRequestSanctionEnforcementService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestSanctionEnforcementService(
                serviceRequestMapper,
                referenceDataService);
    }

    @Test
    void permanentSuspensionCancelsOpenRequestWithCanceledStatusContract() {
        ServiceRequestSanctionTarget target = target(1256L, "SVCC0002", null, null);
        when(serviceRequestMapper.findSanctionTargetsByOwnerForUpdate(7L))
                .thenReturn(List.of(target));
        when(serviceRequestMapper.adminCancelOpenServiceRequest(1256L, "99"))
                .thenReturn(1);

        var impacts = service.cancelOwnedForPermanentSuspension(command());

        verify(referenceDataService).requireActiveCode("SVCG01", "SVCC0006");
        verify(serviceRequestMapper).adminCancelOpenServiceRequest(1256L, "99");
        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.actionCode()).isEqualTo("CANCELED");
            assertThat(impact.previousStatusCode()).isEqualTo("SVCC0002");
        });
    }

    @Test
    void permanentSuspensionDoesNotCancelMatchedRequestWhileTradeIsActive() {
        ServiceRequestSanctionTarget target = target(1257L, "SVCC0003", 3001L, "TRDC0003");
        when(serviceRequestMapper.findSanctionTargetsByOwnerForUpdate(7L))
                .thenReturn(List.of(target));

        var impacts = service.cancelOwnedForPermanentSuspension(command());

        verify(serviceRequestMapper, never())
                .closeMatchedOrHeldServiceRequestForSanction(1257L, "SVCC0003", "99");
        assertThat(impacts).singleElement().satisfies(impact ->
                assertThat(impact.actionCode()).isEqualTo("HELD_FOR_REVIEW"));
    }

    private MemberServiceRequestEnforcementCommand command() {
        return new MemberServiceRequestEnforcementCommand(7L, 99L, "영구 이용정지", null);
    }

    private ServiceRequestSanctionTarget target(
            Long serviceRequestId,
            String statusCode,
            Long tradeId,
            String tradeStatusCode) {
        ServiceRequestSanctionTarget target = new ServiceRequestSanctionTarget();
        target.setServiceRequestId(serviceRequestId);
        target.setOwnerUserSn(7L);
        target.setStatusCode(statusCode);
        target.setLinkedTradeSn(tradeId);
        target.setLinkedTradeStatusCode(tradeStatusCode);
        return target;
    }
}
