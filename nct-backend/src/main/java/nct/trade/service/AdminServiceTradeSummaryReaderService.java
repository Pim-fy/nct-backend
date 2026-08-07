package nct.trade.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.AdminServiceTradeSummaryReader;

/** 담당자 7 · F-OPS-021: 서비스 요청별 거래 상태를 검증해 운영 도메인에 제공합니다. */
@Service
@RequiredArgsConstructor
public class AdminServiceTradeSummaryReaderService implements AdminServiceTradeSummaryReader {
    private static final Set<String> SUPPORTED_TRADE_STATUSES = Set.of(
            "TRDC0003", "TRDC0004", "TRDC0005", "TRDC0006", "TRDC0007", "TRDC0008");

    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminServiceTradeSummary> findSummaries(List<Long> serviceRequestIds) {
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return Map.of();
        }
        if (serviceRequestIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, AdminServiceTradeSummary> summaries = new LinkedHashMap<>();
        for (AdminServiceTradeSummary summary : tradeMapper.findAdminServiceTradeSummaries(
                serviceRequestIds.stream().distinct().toList())) {
            validate(summary);
            if (summaries.putIfAbsent(summary.getServiceRequestId(), summary) != null) {
                throw inconsistent("한 서비스 요청에 서비스 거래가 여러 건 연결되어 있습니다.");
            }
        }
        return Map.copyOf(summaries);
    }

    private void validate(AdminServiceTradeSummary summary) {
        if (summary == null
                || summary.getServiceRequestId() == null
                || summary.getServiceRequestId() <= 0
                || summary.getTradeId() == null
                || summary.getTradeId() <= 0
                || summary.getQuoteId() == null
                || summary.getQuoteId() <= 0
                || !SUPPORTED_TRADE_STATUSES.contains(summary.getTradeStatusCode())
                || summary.getActiveDisputeCount() < 0
                || summary.getActiveDisputeCount() > 1
                || summary.getUnsupportedDisputeCount() != 0) {
            throw inconsistent("서비스 거래 통합상태 데이터가 일관되지 않습니다.");
        }
        boolean hasActiveDispute = summary.getActiveDisputeCount() == 1;
        if (hasActiveDispute != (summary.getActiveDisputeId() != null)
                || hasActiveDispute != (summary.getActiveDisputeStatusCode() != null)) {
            throw inconsistent("서비스 거래 분쟁 상태가 일관되지 않습니다.");
        }
    }

    private CustomException inconsistent(String message) {
        return new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
}
