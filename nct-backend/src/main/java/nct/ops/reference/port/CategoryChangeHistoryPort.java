package nct.ops.reference.port;

/** 담당자 7 · CATEGORY 변경 이력을 담당자 6의 공용 감사 저장소에 연결하는 경계다. */
public interface CategoryChangeHistoryPort {
    void record(CategoryChangeHistoryCommand command);
}
