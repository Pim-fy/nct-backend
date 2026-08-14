package nct.abuse.port;

/**
 * 담당자 7 · F-OPS-007/F-PAY-010/F-PAY-012:
 * 접수·처리 중인 신고의 피신고자인지 다른 도메인이 확인하는 읽기 계약입니다.
 */
public interface ActiveReportedUserReader {

    boolean hasActiveReportAgainst(Long reportedUserSn);
}
