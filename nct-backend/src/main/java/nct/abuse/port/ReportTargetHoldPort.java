package nct.abuse.port;

/** 담당자 7 · F-OPS-007: 신고된 업무 대상 한 건을 보류하고 원래 상태로 복구하는 계약입니다. */
public interface ReportTargetHoldPort {

    String referenceTypeCode();

    boolean lock(Long referenceSn);

    ReportTargetHoldResult pause(Long referenceSn, String actorId);

    boolean restore(ReportTargetRestoreCommand command);
}
