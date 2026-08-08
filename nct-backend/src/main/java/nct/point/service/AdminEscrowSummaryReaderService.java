package nct.point.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.dto.AdminEscrowSummary;
import nct.point.mapper.PointMapper;
import nct.point.port.AdminEscrowSummaryReader;

/** 담당자 7 · F-OPS-021: 원장 합계의 부호와 중복을 검증해 운영 도메인에 제공합니다. */
@Service
@RequiredArgsConstructor
public class AdminEscrowSummaryReaderService implements AdminEscrowSummaryReader {

    private final PointMapper pointMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminEscrowSummary> findSummaries(List<Long> tradeIds) {
        if (tradeIds == null || tradeIds.isEmpty()) {
            return Map.of();
        }
        if (tradeIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, AdminEscrowSummary> summaries = new LinkedHashMap<>();
        for (AdminEscrowSummary summary : pointMapper.findAdminEscrowSummaries(
                tradeIds.stream().distinct().toList())) {
            if (summary == null
                    || summary.getTradeId() == null
                    || summary.getTradeId() <= 0
                    || summary.getEscrowDebitedAmount() <= 0
                    || summary.getRefundedAmount() < 0
                    || summary.getEscrowLedgerAmount() > 0
                    || summary.getSettledAmount() < 0
                    || summary.getEscrowLedgerAmount()
                            != -summary.getEscrowDebitedAmount() + summary.getRefundedAmount()
                    || summary.getRefundedAmount() > 0
                            && summary.getRefundedAmount() != summary.getEscrowDebitedAmount()
                    || summary.getSettledAmount() > 0
                            && summary.getSettledAmount() != summary.getEscrowDebitedAmount()
                    || summary.getRefundedAmount() > 0 && summary.getSettledAmount() > 0
                    || summaries.putIfAbsent(summary.getTradeId(), summary) != null) {
                throw new CustomException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "거래별 보관금 통합상태 데이터가 일관되지 않습니다.");
            }
        }
        return Map.copyOf(summaries);
    }
}
