package nct.ops.reference.port;

/** 담당자 7 · CMM_CODE 변경 이력을 담당자 6의 공용 감사 저장소에 연결하는 경계입니다. */
public interface BidUnitChangeHistoryPort {
    void record(BidUnitChangeHistoryCommand command);
}
