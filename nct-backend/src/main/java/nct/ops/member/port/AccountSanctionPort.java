package nct.ops.member.port;

import java.util.List;

/**
 * 담당자 7 · F-OPS-002/019: 회원관리 오케스트레이션이 소비하는 담당자 5 SANCTION 계약입니다.
 * 구현체는 requestId 기준 멱등성과 동일 트랜잭션 참여를 보장해야 합니다.
 */
public interface AccountSanctionPort {

    void restrict(AccountSanctionCommand command);

    void release(AccountSanctionCommand command);

    List<AccountSanctionHistory> findHistory(Long userSn, int limit);
}
