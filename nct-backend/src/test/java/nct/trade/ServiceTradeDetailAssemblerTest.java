package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.service.ServiceTradeDetailAssembler;

class ServiceTradeDetailAssemblerTest {

    private final ServiceTradeDetailAssembler assembler = new ServiceTradeDetailAssembler();

    @Test
    void providerInProgressSeesOnlyImplementedCompletionAndDisputeActions() {
        ServiceTradeDetailResponse response = assembler.assemble(source("TRDC0003"), 22L);

        assertThat(response.viewerRole()).isEqualTo("PROVIDER");
        assertThat(response.availableActions()).containsExactly(
                "REQUEST_COMPLETION",
                "SUBMIT_DISPUTE");
    }

    @Test
    void requesterWaitingConfirmationSeesConfirmAndDisputeActions() {
        ServiceTradeDetailResponse response = assembler.assemble(source("TRDC0005"), 11L);

        assertThat(response.viewerRole()).isEqualTo("REQUESTER");
        assertThat(response.availableActions()).containsExactly("CONFIRM_COMPLETION", "SUBMIT_DISPUTE");
    }

    @Test
    void nonPartyCannotAssembleServiceTradeDetail() {
        assertThatThrownBy(() -> assembler.assemble(source("TRDC0003"), 33L))
                .isInstanceOf(CustomException.class);
    }

    private ServiceTradeDetailSource source(String statusCode) {
        return new ServiceTradeDetailSource(
                91L,
                11L,
                22L,
                31L,
                statusCode,
                BigDecimal.valueOf(150000),
                LocalDateTime.of(2026, 8, 5, 12, 0),
                "이사 전 입주 청소 요청",
                "깨끗한 청소 · 150,000원",
                "2026. 08. 03. 오전 10:00",
                "ESCROW_HELD",
                "보관금이 안전하게 보관 중입니다.");
    }
}
