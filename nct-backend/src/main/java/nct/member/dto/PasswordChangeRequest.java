package nct.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import nct.global.validation.PasswordPolicy;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** ISS-022: 로그인 상태 비밀번호 변경 요청이다. 세션이 이미 본인확인을 증명하므로 현재 비밀번호 재확인만 추가로 받는다. */
@Getter
@Setter
public class PasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호는 필수입니다.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.MESSAGE)
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
    private String newPasswordConfirm;
}
