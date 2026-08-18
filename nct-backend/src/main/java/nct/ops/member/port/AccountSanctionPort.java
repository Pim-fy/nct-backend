package nct.ops.member.port;

import java.util.List;

/**
 * 담당자 7 · F-OPS-002/019: 회원관리 오케스트레이션이 소비하는 담당자 5 SANCTION 계약입니다.
 * 구현체는 requestId 기준 멱등성과 동일 트랜잭션 참여를 보장해야 합니다.
 */
public interface AccountSanctionPort {

    /**
     * 수동 계정 제재를 새로 만들었으면 true, 이미 처리된 요청 또는 기존 활성 제재면 false를 반환합니다.
     * 호출자는 false가 상태 변경 뒤 확인된 경우 전체 트랜잭션을 롤백해야 합니다.
     */
    boolean restrict(AccountSanctionCommand command);

    /** 담당자 7 · F-OPS-002: 이 호출에서 수동 계정 제재가 실제 해제됐는지 반환합니다. */
    boolean release(AccountSanctionCommand command);

    boolean hasActiveReportSanction(Long userSn);

    boolean hasActiveSuspension(Long userSn);

    List<AccountSanctionHistory> findHistory(Long userSn, int limit);
}
