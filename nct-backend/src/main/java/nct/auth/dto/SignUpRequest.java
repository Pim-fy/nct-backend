package nct.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @Pattern(regexp = "^$|^01[016789]-\\d{3,4}-\\d{4}$",
             message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String telno;
}
