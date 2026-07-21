package nct.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// @ai_generated: 작업단위5(F-AUTH-004 온보딩, ISS-009)
/** 소셜 최초 가입 온보딩 완료 요청 - 온보딩 토큰은 쿠키로 전달되므로 본문에 없다 */
@Getter
@Setter
public class OauthOnboardingRequest {

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 100, message = "닉네임은 100자 이하여야 합니다.")
    private String nickname;

    @NotEmpty(message = "약관 동의 결과는 필수입니다.")
    @Valid
    private List<AgreementRequest> agreements;
}
