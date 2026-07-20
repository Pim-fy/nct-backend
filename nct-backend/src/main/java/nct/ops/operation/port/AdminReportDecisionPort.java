package nct.ops.operation.port;

/**
 * 담당자 7 · F-OPS-007 신고 처리 어댑터입니다.
 *
 * <p>담당자 7의 관리자 화면/API는 이 인터페이스만 호출합니다. ABUSE_REPORT Mapper를
 * 직접 호출하지 않으며, 담당자 5가 실제 구현을 제공할 때 이 포트를 구현하면 됩니다.</p>
 */
public interface AdminReportDecisionPort {

    void decide(AdminReportDecisionCommand command);
}
