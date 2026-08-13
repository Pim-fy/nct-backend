package nct.trade.service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.trade.dto.AdminReportTradeReference;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.AdminReportTradeReferenceReader;

/** 담당자 7 · F-OPS-007: 신고 목록의 거래 참조를 한 번의 배치 조회로 보강합니다. */
@Service
@RequiredArgsConstructor
public class AdminReportTradeReferenceReaderService implements AdminReportTradeReferenceReader {

    private static final int QUERY_BATCH_SIZE = 500;

    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminReportTradeReference> findByTradeSns(Collection<Long> tradeSns) {
        if (tradeSns == null || tradeSns.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = tradeSns.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, AdminReportTradeReference> references = new LinkedHashMap<>();
        for (int start = 0; start < normalizedIds.size(); start += QUERY_BATCH_SIZE) {
            int end = Math.min(start + QUERY_BATCH_SIZE, normalizedIds.size());
            for (AdminReportTradeReference row : tradeMapper.findAdminReportTradeReferences(
                    normalizedIds.subList(start, end))) {
                if (row != null && row.getTradeSn() != null) {
                    references.putIfAbsent(row.getTradeSn(), row);
                }
            }
        }
        return Collections.unmodifiableMap(references);
    }
}
