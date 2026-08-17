package nct.abuse.port;

/** 담당자 7 · F-OPS-007: 신고 보류 이전 상태와 남은 시간으로 단건 대상을 복구합니다. */
public record ReportTargetRestoreCommand(
        Long referenceSn,
        String previousStatusCode,
        Long remainingStartSeconds,
        Long remainingSeconds,
        boolean settlementHoldApplied,
        String actorId) {
}
