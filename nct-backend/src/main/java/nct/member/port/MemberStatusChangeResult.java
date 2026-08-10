package nct.member.port;

/** 담당자 7 · F-OPS-019: 조건부 회원 상태변경의 이전·현재 상태와 변경 여부입니다. */
public record MemberStatusChangeResult(
        String previousStatusCode,
        String currentStatusCode,
        boolean changed) {
}
