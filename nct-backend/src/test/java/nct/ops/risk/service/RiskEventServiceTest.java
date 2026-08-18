package nct.ops.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.domain.RiskEvent;
import nct.ops.risk.mapper.RiskEventMapper;

class RiskEventServiceTest {

    private RiskEventMapper riskEventMapper;
    private ReferenceDataService referenceDataService;
    private RiskEventService service;

    @BeforeEach
    void setUp() {
        riskEventMapper = mock(RiskEventMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new RiskEventService(riskEventMapper, referenceDataService);
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
    void returnsEventCreatedInsideSameDetectionWindow() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 18, 10, 0);
        RiskEventCommand command = new RiskEventCommand(
                "RSKC0005", null, null, "최근 60분 거래 신고 10건 이상 감지", "SYSTEM");
        when(riskEventMapper.findDuplicateIdSince(
                "RSKC0005", null, null, "최근 60분 거래 신고 10건 이상 감지", since))
                .thenReturn(41L);

        RiskEventResult result = service.recordOnceSince(command, since);

        assertThat(result).isEqualTo(new RiskEventResult(41L, false));
        verify(riskEventMapper, never()).insertRiskEvent(any());
    }

    /** 담당자 7 · ISSUE-T7-013: 안전한 운영 식별자와 금액은 민감정보로 재해석하지 않습니다. */
    @Test
    void preservesSafeOperationalIdentifiersAndPointAmountsWithoutRemasking() {
        String safeContent = "민감정보 탐지: requestHash=010-1234-5678, 전체 합계 123456789012P";
        RiskEventCommand command = new RiskEventCommand(
                "RSKC0001", "REFC0004", 10L, safeContent, "SYSTEM");
        when(riskEventMapper.findUnprocessedDuplicateId(
                "RSKC0001", "REFC0004", 10L, safeContent)).thenReturn(null);
        doAnswer(invocation -> {
            RiskEvent event = invocation.getArgument(0);
            event.setRiskEventSn(78L);
            return 1;
        }).when(riskEventMapper).insertRiskEvent(any(RiskEvent.class));

        service.recordOnce(command);

        ArgumentCaptor<RiskEvent> eventCaptor = ArgumentCaptor.forClass(RiskEvent.class);
        verify(riskEventMapper).insertRiskEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getContent()).isEqualTo(safeContent);
    }

    @Test
    void rejectsIncompleteReferencePair() {
        RiskEventCommand command = new RiskEventCommand(
                "RSKC0001", "REFC0004", null, "민감정보 탐지", "SYSTEM");

        assertThatThrownBy(() -> service.recordOnce(command))
                .isInstanceOf(CustomException.class);
        verify(riskEventMapper, never()).insertRiskEvent(any());
    }

    @Test
    void marksLinkedRiskEventProcessed() {
        when(riskEventMapper.markProcessed(77L, "7")).thenReturn(1);

        service.markProcessed(77L, " 7 ");

        verify(riskEventMapper).markProcessed(77L, "7");
    }
}
