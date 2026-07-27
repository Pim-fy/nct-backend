package nct.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-014: 이메일+이름 매칭으로 아이디를 찾는 요청이다. */
@Getter
@Setter
public class FindEmailRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;
}
