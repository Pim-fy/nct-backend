package nct.ops.reference.port;

/** 카테고리 원문 대신 감사에 필요한 안전한 변경 요약만 전달한다. */
public record CategoryChangeHistoryCommand(String action, Long actorUserId, Long categorySn,
                                           String reason, String beforeSummary, String afterSummary) {
}
