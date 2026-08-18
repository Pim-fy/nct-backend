package nct.ops.risk.port;

import java.time.LocalDateTime;
import java.util.List;

/** 담당자 7 · REQ-OPS-011: 정산 소유 영역의 장기 보류 읽기 계약입니다. */
public interface SettlementRiskSignalReader {

    List<SettlementRiskCandidate> findLongHeldSettlements(LocalDateTime cutoff, int limit);
}
