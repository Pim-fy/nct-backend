package nct.ops.funds.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.funds.dto.AdminFundDailyFlowResponse;
import nct.ops.funds.dto.AdminFundDashboardSummaryResponse;
import nct.ops.funds.dto.AdminFundSnapshot;
import nct.ops.funds.mapper.AdminFundDashboardMapper;

@Service
@RequiredArgsConstructor
public class AdminFundDashboardService {

    private static final int MAX_PERIOD_DAYS = 366;

    private final AdminFundDashboardMapper fundDashboardMapper;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public AdminFundDashboardSummaryResponse getSummary(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 시작일과 종료일을 모두 입력해 주세요.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (periodEnd.isAfter(LocalDate.now(clock))) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 종료일은 오늘보다 늦을 수 없습니다.");
        }

        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1L;
        if (periodDays > MAX_PERIOD_DAYS) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 기간은 최대 1년까지 선택할 수 있습니다.");
        }

        LocalDateTime startAt = periodStart.atStartOfDay();
        LocalDateTime endAt = periodEnd.plusDays(1).atStartOfDay();

        AdminFundSnapshot snapshot = fundDashboardMapper.findSnapshot();
        List<AdminFundDailyFlowResponse> dailyFlows = fillMissingDates(
                periodStart,
                periodEnd,
                fundDashboardMapper.findDailyFlows(startAt, endAt));

        long periodChargeAmount = dailyFlows.stream()
                .mapToLong(AdminFundDailyFlowResponse::getChargeAmount)
                .sum();
        long periodExchangePaidAmount = dailyFlows.stream()
                .mapToLong(AdminFundDailyFlowResponse::getExchangePaidAmount)
                .sum();
        long periodCommissionAmount = dailyFlows.stream()
                .mapToLong(AdminFundDailyFlowResponse::getCommissionAmount)
                .sum();
        long periodAuctionTradeAmount = dailyFlows.stream()
                .mapToLong(AdminFundDailyFlowResponse::getAuctionTradeAmount)
                .sum();
        long periodServiceTradeAmount = dailyFlows.stream()
                .mapToLong(AdminFundDailyFlowResponse::getServiceTradeAmount)
                .sum();

        return AdminFundDashboardSummaryResponse.builder()
                .generatedAt(LocalDateTime.now(clock))
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .periodDays(Math.toIntExact(periodDays))
                .periodChargeAmount(periodChargeAmount)
                .periodExchangePaidAmount(periodExchangePaidAmount)
                .periodCommissionAmount(periodCommissionAmount)
                .periodAuctionTradeAmount(periodAuctionTradeAmount)
                .periodServiceTradeAmount(periodServiceTradeAmount)
                .activeEscrowAmount(snapshot.getActiveEscrowAmount())
                .heldSettlementAmount(snapshot.getHeldSettlementAmount())
                .heldSettlementCount(snapshot.getHeldSettlementCount())
                .pendingExchangeAmount(snapshot.getPendingExchangeAmount())
                .pendingExchangeCount(snapshot.getPendingExchangeCount())
                .availablePointBalance(snapshot.getAvailablePointBalance())
                .holdPointBalance(snapshot.getHoldPointBalance())
                .attentionHoldAmount(snapshot.getAttentionHoldAmount())
                .attentionHoldCount(snapshot.getAttentionHoldCount())
                .settleablePointBalance(snapshot.getSettleablePointBalance())
                .dailyFlows(dailyFlows)
                .build();
    }

    private List<AdminFundDailyFlowResponse> fillMissingDates(
            LocalDate start,
            LocalDate end,
            List<AdminFundDailyFlowResponse> source) {
        Map<LocalDate, AdminFundDailyFlowResponse> byDate = new LinkedHashMap<>();
        if (source != null) {
            for (AdminFundDailyFlowResponse flow : source) {
                if (flow != null && flow.getDate() != null) {
                    byDate.put(flow.getDate(), flow);
                }
            }
        }

        return start.datesUntil(end.plusDays(1))
                .map(date -> byDate.getOrDefault(date, AdminFundDailyFlowResponse.builder()
                        .date(date)
                        .build()))
                .toList();
    }
}
