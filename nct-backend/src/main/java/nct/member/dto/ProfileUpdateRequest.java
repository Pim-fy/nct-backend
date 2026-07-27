package nct.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-010: 프로필 기본 정보 수정 요청이다. 대상 필드는 POL-AUTH-014로 확정된 4개뿐이다. */
@Getter
@Setter
public class ProfileUpdateRequest {

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
    private String nickname;

    /** 신규 업로드된 프로필 이미지의 FILES.FL_SN (업로드 자체는 담당자6 FILES 계약을 통해 별도로 처리됨) */
    private Long profileFileSn;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Size(max = 100, message = "은행명은 100자를 초과할 수 없습니다.")
    private String bankName;

    @Size(max = 50, message = "계좌번호는 50자를 초과할 수 없습니다.")
    private String accountNo;
}
