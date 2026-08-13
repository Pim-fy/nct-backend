package nct.auth.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** F-AUTH-014: 로컬 로그인ID 또는 소셜 전용 계정의 실제 로그인 제공자를 안내한다. */
@Getter
@Builder
public class FindEmailResponse {

    private final String accountType;

    private final String maskedLoginId;

    private final List<String> oauthProviders;
}
