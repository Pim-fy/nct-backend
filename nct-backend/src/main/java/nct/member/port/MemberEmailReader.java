package nct.member.port;

// @ai_generated
/**
 * F-AUTH-017/POL-AUTH-016: 다른 도메인이 USERS를 직접 조회하지 않고 회원 이메일(평문)을
 * 소비하는 계약이다. 이메일은 민감정보라 AdminMemberIdentityReader(비민감 식별정보)와
 * 분리한다 - 정지 계정 문의 답변 통보처럼 실제 발송이 필요한 좁은 용도로만 쓴다.
 */
public interface MemberEmailReader {

    /** 존재하지 않으면 null. */
    String findEmailByUserSn(Long usrSn);
}
