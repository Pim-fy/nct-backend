package nct.member.port;

// @ai_generated
/**
 * F-AUTH-011/POL-AUTH-013: 회원 탈퇴 시 본인 미답변 1:1 문의를 정리하는 계약이다.
 * customerinquiry 모듈이 이미 member 모듈을 참조하고 있어(AdminMemberIdentityReader),
 * MemberService가 CustomerInquiryService를 직접 참조하면 순환 의존이 생긴다 - 이 포트를
 * member 모듈에 두고 CustomerInquiryService가 구현하는 방식으로 방향을 맞춘다.
 */
public interface CustomerInquiryWithdrawalPort {

    /** 접수·처리중 상태인 본인 문의를 종결 상태로 전환한다. 감사 로그는 구현체가 남긴다. */
    void closeUnansweredByUser(Long usrSn);
}
