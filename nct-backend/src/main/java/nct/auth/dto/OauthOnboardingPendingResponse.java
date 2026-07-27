package nct.auth.dto;

import lombok.Builder;
import lombok.Getter;

// @ai_generated: 작업단위5(F-AUTH-004 온보딩) - 온보딩 화면이 닉네임 기본값을 채우기 위한 조회 응답.
// 온보딩 토큰은 httpOnly 쿠키라 프론트 JS가 직접 못 읽으므로, 이 API로 필요한 값만 내려준다
// (email은 내려주지 않는다 - 온보딩 화면에 이메일 노출·수정 UI가 없어 불필요한 노출).
@Getter
@Builder
public class OauthOnboardingPendingResponse {

    private final String nickname;
    private final String provider;
}
