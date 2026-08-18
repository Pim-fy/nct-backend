package nct.ops.risk.port;

import java.time.LocalDateTime;

/** 담당자 7 · REQ-OPS-011: 장기 보류 판정에 필요한 최소 정산 정보입니다. */
public record SettlementRiskCandidate(
        long settlementSn,
        long tradeSn,
        LocalDateTime holdStartedAt) {
}
