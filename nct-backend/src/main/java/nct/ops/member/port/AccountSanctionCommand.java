package nct.ops.member.port;

/**
 * 담당자 7 · F-OPS-019: 담당자 5의 SANCTION 쓰기 구현에 전달할 계정 제한 명령입니다.
 */
public record AccountSanctionCommand(
        Long userSn,
        Long adminUserSn,
        String reason,
        String requestId) {
}
