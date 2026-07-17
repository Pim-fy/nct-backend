package nct.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @ai_generated
/** 성공 가입 시 USER_AGREE에 저장하는 고정 약관 동의 이력이다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAgreement {

    private Long usrSn;
    private String usrAgrTypeCd;
    private char usrAgrYn;
}
