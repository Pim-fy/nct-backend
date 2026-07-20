package nct.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-011: 정지 계정은 로그인이 차단돼 있어, 로그인ID+이메일 본인확인 후 이메일 링크로 탈퇴를 요청한다. */
@Getter
@Setter
public class WithdrawalLinkRequestDto {

    @NotBlank(message = "로그인 아이디는 필수입니다.")
    private String loginId;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
