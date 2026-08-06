package nct.ops.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.dto.AdminRiskEventListItemResponse;
import nct.ops.risk.mapper.RiskEventMapper;

/** 담당자 7 · F-OPS-011: 목록 필터·페이징의 기본 안전장치 검증입니다. */
class AdminRiskEventServiceTest {

    private RiskEventMapper riskEventMapper;
    private ReferenceDataService referenceDataService;
    private AdminRiskEventService service;

    @BeforeEach
    void setUp() {
        riskEventMapper = mock(RiskEventMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new AdminRiskEventService(riskEventMapper, referenceDataService);
    }

    @Test
    void filtersByActiveTypeAndUnprocessedStatus() {
        AdminRiskEventListItemResponse item = new AdminRiskEventListItemResponse();
        item.setRiskEventId(11L);
        when(riskEventMapper.countAdminRiskEvents("RSKC0001", "N", "keyword")).thenReturn(1L);
        when(riskEventMapper.findAdminRiskEvents("RSKC0001", "N", "keyword", 0L, 20))
                .thenReturn(List.of(item));

        var result = service.getRiskEvents(" RSKC0001 ", "n", " keyword ", 1, 20);

        assertThat(result.items()).containsExactly(item);
        verify(referenceDataService).requireActiveCode("RSKG01", "RSKC0001");
    }

    @Test
    void rejectsInvalidProcessedFilter() {
        assertThatThrownBy(() -> service.getRiskEvents(null, "waiting", null, 1, 20))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsExcessiveKeyword() {
        assertThatThrownBy(() -> service.getRiskEvents(null, null, "x".repeat(101), 1, 20))
                .isInstanceOf(CustomException.class);
    }
}
