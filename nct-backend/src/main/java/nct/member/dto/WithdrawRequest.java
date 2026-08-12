package nct.member.dto;

import lombok.Getter;
import lombok.Setter;

// @ai_generated
/**
 * F-AUTH-011: 활성 계정 탈퇴 요청이다. 로그인 세션이 이미 본인확인을 증명하므로 비밀번호 재확인만 받는다.
 * 소셜 전용 계정은 비밀번호가 없어 빈 문자열로 보낼 수 있으므로(MemberService.withdrawActive가
 * 시스템 생성 로그인ID면 이 필드를 검증하지 않는다) @NotBlank를 두지 않는다 - 2026-08-12 QA 후속.
 */
@Getter
@Setter
public class WithdrawRequest {

    private String currentPassword;
}
