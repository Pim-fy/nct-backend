package nct.member.port;

/** 담당자 7 · F-OPS-019: 회원 상태와 로그인 세션을 함께 변경하기 위한 AUTH 명령입니다. */
public record MemberStatusChangeCommand(
        Long userSn,
        String targetStatusCode,
        Long actorUserSn) {
}
