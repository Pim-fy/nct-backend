package nct.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * [회원가입 요청]
 * - @Valid 검증 실패 시 GlobalExceptionHandler 가 필드별 오류 목록으로 응답
 */
@Getter
@Setter
public class SignUpRequest {

    // @ai_generated: 이메일과 분리된 로컬 로그인 ID
    @NotBlank(message = "로그인 아이디는 필수입니다.")
    @Size(min = 6, max = 50, message = "로그인 아이디는 6~50자여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "로그인 아이디는 영문, 숫자, . _ - 만 사용할 수 있습니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 100, message = "닉네임은 100자 이하여야 합니다.")
    private String nickname;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Pattern(regexp = "^$|^01[016789]-\\d{3,4}-\\d{4}$",
             message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String telno;

    // @ai_generated: 약관 3종을 성공 가입 시에만 USER_AGREE로 저장한다.
    @NotEmpty(message = "약관 동의 결과는 필수입니다.")
    @Valid
    private List<AgreementRequest> agreements;

    @NotNull(message = "이메일 인증 식별자는 필수입니다.")
    private Long verificationId;
}
