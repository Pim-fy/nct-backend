package nct.ops.member.port;

import java.time.LocalDateTime;

/** 담당자 7 · F-OPS-002: 담당자 5가 제공할 회원 제재 이력 조회 결과입니다. */
public record AccountSanctionHistory(
        Long sanctionSn,
        String sanctionTypeCode,
        String sanctionTypeName,
        String reason,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long processedBy) {
}
