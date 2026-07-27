package nct.member.dto;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** F-AUTH-010: 프로필 수정 성공 후 갱신된 값을 그대로 되돌려준다(화면 즉시 반영용). */
@Getter
@Builder
public class ProfileUpdateResponse {

    private String nickname;
    private Long profileFileSn;
    private String profileImageUrl;
    private String email;
    private String bankName;
    private String accountNo;
    private String phone;
    private String zip;
    private String address;
    private String addressDetail;
    // ISS-022: 소셜 로그인 전용(시스템 생성 로그인ID) 계정은 실제 비밀번호가 없어 변경 UI를 숨겨야 한다.
    private boolean passwordChangeable;
}
