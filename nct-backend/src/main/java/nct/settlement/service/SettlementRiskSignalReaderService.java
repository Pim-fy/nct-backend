package nct.settlement.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.ops.risk.port.SettlementRiskCandidate;
import nct.ops.risk.port.SettlementRiskSignalReader;
import nct.settlement.mapper.SettlementMapper;

/** 담당자 7 연계 · REQ-OPS-011: 정산 테이블을 직접 노출하지 않는 읽기 전용 계약입니다. */
@Service
@RequiredArgsConstructor
public class SettlementRiskSignalReaderService implements SettlementRiskSignalReader {

    private final SettlementMapper settlementMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SettlementRiskCandidate> findLongHeldSettlements(LocalDateTime cutoff, int limit) {
        return settlementMapper.selectLongHeldSettlements(cutoff, limit);
    }
}
