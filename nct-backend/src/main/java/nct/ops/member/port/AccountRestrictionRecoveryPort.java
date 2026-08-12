package nct.ops.member.port;

/**
 * 담당자 7 - F-OPS-007/019: 마지막 계정 제재가 끝난 뒤 신고 제재로 멈춘 업무를 복구하는 계약입니다.
 */
public interface AccountRestrictionRecoveryPort {

    void restorePending(Long userSn, Long adminUserSn, String reason);
}
