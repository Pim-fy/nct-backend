package nct.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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

    // @ai_generated: ISS-023 - OAuth 온보딩도 로컬 가입과 동일하게 전화번호를 필수로 전환.
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^0\\d{10}$", message = "전화번호는 0으로 시작하는 11자리 숫자여야 합니다.")
    private String telno;

    @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
    private String address;

    @Size(max = 200, message = "상세주소는 200자 이하여야 합니다.")
    private String detailAddress;

    @Pattern(regexp = "^$|^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
    private String zip;

    @Size(max = 100, message = "은행명은 100자 이하여야 합니다.")
    private String bankName;

    @Size(max = 50, message = "계좌번호는 50자 이하여야 합니다.")
    private String accountNo;

    @NotEmpty(message = "약관 동의 결과는 필수입니다.")
    @Valid
    private List<AgreementRequest> agreements;
}
