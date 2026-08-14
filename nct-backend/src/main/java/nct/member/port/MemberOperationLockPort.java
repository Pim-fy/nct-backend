package nct.member.port;

/** 담당자 7: 회원 단위 상태 변경과 금전성 명령을 같은 USERS 행 잠금으로 직렬화합니다. */
public interface MemberOperationLockPort {

    void lock(Long userSn);
}
