package nct.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-011: 활성 계정 탈퇴 요청이다. 로그인 세션이 이미 본인확인을 증명하므로 비밀번호 재확인만 받는다. */
@Getter
@Setter
public class WithdrawRequest {

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String currentPassword;
}
