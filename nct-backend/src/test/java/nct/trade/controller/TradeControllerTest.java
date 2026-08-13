package nct.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.global.exception.GlobalExceptionHandler;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.ops.security.service.SensitiveDataMasker;
import nct.trade.dto.ServiceTradeCompletionRequest;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.dto.ServiceScheduleChangeRequest;
import nct.trade.dto.ServiceScheduleCancellationRequest;
import nct.trade.service.TradeOfflineScheduleProposalService;
import nct.trade.service.TradeService;

/** F-SVC-014 완료 요청 메모의 HTTP 입력 검증과 서비스 전달 계약을 확인한다. */
class TradeControllerTest {

    private TradeService tradeService;
    private TradeOfflineScheduleProposalService offlineScheduleProposalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tradeService = mock(TradeService.class);
        offlineScheduleProposalService = mock(TradeOfflineScheduleProposalService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradeController(tradeService, offlineScheduleProposalService))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    @Test
    void forwardsCompletionMemoAndReturnsOk() {
        ServiceTradeCompletionRequest request = new ServiceTradeCompletionRequest();
        request.setCompletionMemo("에어컨 분해 청소와 시운전을 완료했습니다.");

        var response = new TradeController(tradeService, offlineScheduleProposalService)
                .requestServiceCompletion(81L, request, providerUserDetails(22L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(tradeService).requestServiceCompletion(
                81L, 22L, "에어컨 분해 청소와 시운전을 완료했습니다.");
    }

    @Test
    void forwardsCommonTradeReportAndReturnsOk() {
        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setReportTypeCode("ABRC0009");
        request.setContent("배송 중 상품이 파손되었습니다.");

        var response = new TradeController(tradeService, offlineScheduleProposalService)
                .registerTradeReport(81L, request, providerUserDetails(22L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(tradeService).registerTradeReport(81L, 22L, request);
    }

    @Test
    void rejectsBlankCommonTradeReportBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/trades/81/reports")
                        .contentType("application/json")
                        .content("{\"reportTypeCode\":\"ABRC0009\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tradeService);
    }

    @Test
    void rejectsMissingCompletionMemoBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/trades/81/service-completion-requests")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tradeService);
    }

    @Test
    void rejectsBlankCompletionMemoBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/trades/81/service-completion-requests")
                        .contentType("application/json")
                        .content("{\"completionMemo\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tradeService);
    }

    @Test
    void rejectsOverlongCompletionMemoBeforeCallingService() throws Exception {
        String memo = "가".repeat(1001);

        mockMvc.perform(post("/api/trades/81/service-completion-requests")
                        .contentType("application/json")
                        .content("{\"completionMemo\":\"" + memo + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tradeService);
    }

    @Test
    void forwardsServiceScheduleChangeRequestAndReturnsOk() {
        ServiceScheduleChangeRequest request = new ServiceScheduleChangeRequest();
        request.setRequestedScheduleAt(java.time.LocalDateTime.of(2026, 8, 10, 14, 0));
        request.setReason("오후로 변경 부탁드립니다.");

        var response = new TradeController(tradeService, offlineScheduleProposalService)
                .requestServiceScheduleChange(81L, request, providerUserDetails(22L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(tradeService).requestServiceScheduleChange(
                81L,
                22L,
                new nct.trade.dto.ServiceScheduleChangeCommand(
                        java.time.LocalDateTime.of(2026, 8, 10, 14, 0),
                        "오후로 변경 부탁드립니다."));
    }

    @Test
    void forwardsServiceScheduleCancellationRequestAndReturnsOk() {
        ServiceScheduleCancellationRequest request = new ServiceScheduleCancellationRequest();
        request.setReason("일정 취소가 필요합니다.");

        var response = new TradeController(tradeService, offlineScheduleProposalService)
                .requestServiceScheduleCancellation(81L, request, providerUserDetails(22L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(tradeService).requestServiceScheduleCancellation(
                81L,
                22L,
                new nct.trade.dto.ServiceScheduleCancellationCommand("일정 취소가 필요합니다."));
    }

    private CustomUserDetails providerUserDetails(long userId) {
        return new CustomUserDetails(AuthMember.builder()
                .id(userId)
                .email("provider@example.com")
                .password("{noop}test")
                .role("ROLE_SERVICE")
                .status("USRC0001")
                .build());
    }
}
