package nct.ops.funds.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.ops.funds.dto.AdminFundDailyFlowResponse;
import nct.ops.funds.dto.AdminFundDashboardSummaryResponse;
import nct.ops.funds.dto.AdminFundSnapshot;
import nct.ops.funds.mapper.AdminFundDashboardMapper;

@ExtendWith(MockitoExtension.class)
class AdminFundDashboardServiceTest {

    @Mock
    private AdminFundDashboardMapper fundDashboardMapper;

    private AdminFundDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminFundDashboardService(fundDashboardMapper);
    }

    @Test
    void getSummaryFillsDatesAndCalculatesPeriodTotals() {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusDays(9);
        when(fundDashboardMapper.findSnapshot()).thenReturn(AdminFundSnapshot.builder()
                .activeEscrowAmount(42000L)
                .pendingExchangeAmount(20000L)
                .attentionHoldAmount(1000L)
                .attentionHoldCount(1L)
                .build());
        when(fundDashboardMapper.findDailyFlows(any(), any())).thenReturn(List.of(
                AdminFundDailyFlowResponse.builder()
                        .date(today)
                        .chargeAmount(50000L)
                        .exchangePaidAmount(10000L)
                        .commissionAmount(2500L)
                        .auctionTradeAmount(30000L)
                        .serviceTradeAmount(12000L)
                        .build()));

        AdminFundDashboardSummaryResponse response = service.getSummary(periodStart, today);

        assertThat(response.getDailyFlows()).hasSize(10);
        assertThat(response.getPeriodStart()).isEqualTo(periodStart);
        assertThat(response.getPeriodEnd()).isEqualTo(today);
        assertThat(response.getPeriodDays()).isEqualTo(10);
        assertThat(response.getDailyFlows().getLast().getDate()).isEqualTo(today);
        assertThat(response.getPeriodChargeAmount()).isEqualTo(50000L);
        assertThat(response.getPeriodExchangePaidAmount()).isEqualTo(10000L);
        assertThat(response.getPeriodCommissionAmount()).isEqualTo(2500L);
        assertThat(response.getPeriodAuctionTradeAmount()).isEqualTo(30000L);
        assertThat(response.getPeriodServiceTradeAmount()).isEqualTo(12000L);
        assertThat(response.getActiveEscrowAmount()).isEqualTo(42000L);
        assertThat(response.getPendingExchangeAmount()).isEqualTo(20000L);
        assertThat(response.getAttentionHoldAmount()).isEqualTo(1000L);
        assertThat(response.getAttentionHoldCount()).isEqualTo(1L);
    }

    @Test
    void getSummaryRejectsReversedPeriod() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.getSummary(today, today.minusDays(1)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시작일은 종료일보다 늦을 수 없습니다");
    }

    @Test
    void getSummaryRejectsPeriodLongerThanOneYear() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.getSummary(today.minusDays(366), today))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("최대 1년");
    }

    @Test
    void getSummaryRejectsFutureEndDate() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.getSummary(today, today.plusDays(1)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("오늘보다 늦을 수 없습니다");
    }
}
