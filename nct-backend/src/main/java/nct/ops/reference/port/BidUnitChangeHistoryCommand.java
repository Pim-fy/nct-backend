package nct.ops.reference.port;

/** 담당자 7 · 입찰 단위 변경 결과를 공용 감사 계약으로 전달하는 명령입니다. */
public record BidUnitChangeHistoryCommand(
        String action,
        Long actorUserId,
        Long bidUnitSn,
        String reason,
        String beforeSummary,
        String afterSummary) {
}
