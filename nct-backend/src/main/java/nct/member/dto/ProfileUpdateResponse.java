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
    private String email;
    private String bankName;
    private String accountNo;
}
