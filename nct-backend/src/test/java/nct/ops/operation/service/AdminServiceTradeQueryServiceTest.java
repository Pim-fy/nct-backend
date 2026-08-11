package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import nct.ops.security.service.SensitiveDataMasker;
import nct.trade.dto.ServiceScheduleHistoryItem;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.port.AdminServiceTradeDetailReader;

/** 담당자 7 · F-OPS-005: 관리자 거래 상세의 개인정보 마스킹과 읽기 전용 응답을 검증합니다. */
class AdminServiceTradeQueryServiceTest {

    @Test
    void masksFreeTextAndRemovesPartyOnlyFields() {
        AdminServiceTradeDetailReader reader = mock(AdminServiceTradeDetailReader.class);
        when(reader.findByTradeId(91L)).thenReturn(new ServiceTradeDetailResponse(
                91L,
                31L,
                "ADMIN",
                "TRDC0007",
                BigDecimal.valueOf(150000),
                null,
                "연락처 010-1234-5678",
                "메일 test@example.com",
                "2026. 08. 11.",
                "서울시 상세주소",
                "STLC0002",
                "정산 보류",
                "ACTIVE",
                true,
                List.of(new ServiceScheduleHistoryItem(
                        1L,
                        "CHANGE",
                        LocalDateTime.of(2026, 8, 11, 10, 0),
                        null,
                        "연락처 010-9876-5432",
                        "REQUESTER")),
                List.of("SUBMIT_DISPUTE")));
        AdminServiceTradeQueryService service = new AdminServiceTradeQueryService(
                reader,
                new SensitiveDataMasker());

        ServiceTradeDetailResponse response = service.getDetail(91L);

        assertThat(response.viewerRole()).isEqualTo("ADMIN");
        assertThat(response.serviceRequestTitle()).doesNotContain("010-1234-5678");
        assertThat(response.quoteSummary()).doesNotContain("test@example.com");
        assertThat(response.scheduleHistory().getFirst().reason()).doesNotContain("010-9876-5432");
        assertThat(response.scheduleHistory().getFirst().actorRole()).isEqualTo("REQUESTER");
        assertThat(response.serviceAddressLabel()).isNull();
        assertThat(response.chatRoomStatus()).isEqualTo("ACTIVE");
        assertThat(response.chatAvailable()).isFalse();
        assertThat(response.availableActions()).isEmpty();
    }
}
