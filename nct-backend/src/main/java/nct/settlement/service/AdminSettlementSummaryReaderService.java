package nct.settlement.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.settlement.dto.AdminSettlementSummary;
import nct.settlement.mapper.SettlementMapper;
import nct.settlement.port.AdminSettlementSummaryReader;

/** 담당자 7 · F-OPS-021: 거래별 정산 상태를 검증해 운영 도메인에 제공합니다. */
@Service
@RequiredArgsConstructor
public class AdminSettlementSummaryReaderService implements AdminSettlementSummaryReader {
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            "STLC0001", "STLC0002", "STLC0003", "STLC0004");

    private final SettlementMapper settlementMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminSettlementSummary> findSummaries(List<Long> tradeIds) {
        if (tradeIds == null || tradeIds.isEmpty()) {
            return Map.of();
        }
        if (tradeIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, AdminSettlementSummary> summaries = new LinkedHashMap<>();
        for (AdminSettlementSummary summary : settlementMapper.findAdminSummariesByTradeIds(
                tradeIds.stream().distinct().toList())) {
            if (summary == null
                    || summary.getTradeId() == null
                    || summary.getTradeId() <= 0
                    || summary.getSettlementId() == null
                    || summary.getSettlementId() <= 0
                    || !SUPPORTED_STATUSES.contains(summary.getStatusCode())
                    || summaries.putIfAbsent(summary.getTradeId(), summary) != null) {
                throw new CustomException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "거래별 정산 통합상태 데이터가 일관되지 않습니다.");
            }
        }
        return Map.copyOf(summaries);
    }
}
