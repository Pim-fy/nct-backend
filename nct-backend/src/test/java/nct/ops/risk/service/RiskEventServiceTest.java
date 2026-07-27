package nct.ops.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.domain.RiskEvent;
import nct.ops.risk.mapper.RiskEventMapper;
import nct.ops.security.service.SensitiveDataMasker;

class RiskEventServiceTest {

    private RiskEventMapper riskEventMapper;
    private ReferenceDataService referenceDataService;
    private RiskEventService service;

    @BeforeEach
    void setUp() {
        riskEventMapper = mock(RiskEventMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new RiskEventService(riskEventMapper, referenceDataService,
                new SensitiveDataMasker());
    }

    @Test
    void createsRiskEventOnce() {
        RiskEventCommand command = new RiskEventCommand(
                " RSKC0001 ", " REFC0004 ", 10L, " 민감정보 탐지 ", " SYSTEM ");
        when(riskEventMapper.findUnprocessedDuplicateId(
                "RSKC0001", "REFC0004", 10L, "민감정보 탐지")).thenReturn(null);
        doAnswer(invocation -> {
            RiskEvent event = invocation.getArgument(0);
            event.setRiskEventSn(77L);
            return 1;
        }).when(riskEventMapper).insertRiskEvent(any(RiskEvent.class));

        RiskEventResult result = service.recordOnce(command);

        assertThat(result).isEqualTo(new RiskEventResult(77L, true));
        verify(referenceDataService).requireActiveCode("RSKG01", "RSKC0001");
        verify(referenceDataService).requireActiveCode("REFG01", "REFC0004");
    }

    @Test
    void returnsExistingEventWithoutSecondInsert() {
        RiskEventCommand command = new RiskEventCommand(
                "RSKC0001", "REFC0004", 10L, "민감정보 탐지", "SYSTEM");
        when(riskEventMapper.findUnprocessedDuplicateId(
                "RSKC0001", "REFC0004", 10L, "민감정보 탐지")).thenReturn(31L);

        RiskEventResult result = service.recordOnce(command);

        assertThat(result).isEqualTo(new RiskEventResult(31L, false));
        verify(riskEventMapper, never()).insertRiskEvent(any());
    }

    @Test
    void rejectsIncompleteReferencePair() {
        RiskEventCommand command = new RiskEventCommand(
                "RSKC0001", "REFC0004", null, "민감정보 탐지", "SYSTEM");

        assertThatThrownBy(() -> service.recordOnce(command))
                .isInstanceOf(CustomException.class);
        verify(riskEventMapper, never()).insertRiskEvent(any());
    }
}
