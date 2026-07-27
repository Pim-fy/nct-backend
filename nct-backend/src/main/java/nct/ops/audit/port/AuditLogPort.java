package nct.ops.audit.port;

/**
 * 담당자 7 · F-OPS-015 감사 기록 연결 규격입니다.
 *
 * <p>AUDIT_LOG 소유자인 담당자 6이 구현합니다. 승인·반려 같은 운영 변경은 실제 구현이
 * 연결된 뒤에만 완료 처리하며, 호출자는 원문 개인정보를 전달하지 않습니다.</p>
 */
public interface AuditLogPort {

    void record(AuditLogCommand command);
}
