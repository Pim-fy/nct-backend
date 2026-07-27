package nct.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-011: 정지 계정용 탈퇴 이메일 링크의 토큰을 확정한다. */
@Getter
@Setter
public class WithdrawalConfirmRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    private String token;
}
