package nct.member.port;

/** 담당자 7 · F-OPS-010: 관리자 대시보드에 회원 집계만 제공하는 읽기 계약입니다. */
public interface AdminMemberSummaryReader {

    long countActiveUsers();
}
