package nct.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-010: 프로필 기본 정보 수정 요청이다. 대상 필드는 POL-AUTH-014(ISS-022 개정)로 확정된 6개다. */
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

    // @ai_generated: ISS-023 - SignUpRequest·OauthOnboardingRequest와 동일하게 전화번호를 필수로 전환.
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^0\\d{10}$", message = "전화번호는 0으로 시작하는 11자리 숫자여야 합니다.")
    private String phone;

    @Pattern(regexp = "^$|^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
    private String zip;

    @Size(max = 200, message = "주소는 200자를 초과할 수 없습니다.")
    private String address;

    @Size(max = 200, message = "상세주소는 200자를 초과할 수 없습니다.")
    private String addressDetail;

    // 담당자 7 · F-AUTH-010: null(미전송)과 빈 문자열(명시적 삭제)은 구분하면서 검증 전 공백을 제거한다.
    public void setNickname(String nickname) {
        this.nickname = trimNullable(nickname);
    }

    public void setEmail(String email) {
        this.email = trimNullable(email);
    }

    public void setBankName(String bankName) {
        this.bankName = trimNullable(bankName);
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = trimNullable(accountNo);
    }

    public void setPhone(String phone) {
        this.phone = trimNullable(phone);
    }

    public void setZip(String zip) {
        this.zip = trimNullable(zip);
    }

    public void setAddress(String address) {
        this.address = trimNullable(address);
    }

    public void setAddressDetail(String addressDetail) {
        this.addressDetail = trimNullable(addressDetail);
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
