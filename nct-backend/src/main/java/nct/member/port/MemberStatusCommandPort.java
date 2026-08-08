package nct.member.port;

/** 담당자 7 · F-OPS-019: USERS 소유 영역이 제공하는 상태변경·세션 차단 계약입니다. */
public interface MemberStatusCommandPort {

    MemberStatusChangeResult changeStatus(MemberStatusChangeCommand command);
}
