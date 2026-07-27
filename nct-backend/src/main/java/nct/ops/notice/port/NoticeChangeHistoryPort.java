package nct.ops.notice.port;

/**
 * 공지 변경 이력 저장 경계다.
 * 담당자 6의 AUDIT_LOG 서비스가 도착하면 현재 어댑터 대신 그 구현체를 연결한다.
 */
public interface NoticeChangeHistoryPort {

    void record(NoticeChangeHistoryCommand command);
}
