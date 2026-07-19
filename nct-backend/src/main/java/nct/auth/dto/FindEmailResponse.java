package nct.auth.dto;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** F-AUTH-014: 가입일 등 부가정보 없이 마스킹된 로그인ID만 반환한다. */
@Getter
@Builder
public class FindEmailResponse {

    private final String maskedLoginId;
}
