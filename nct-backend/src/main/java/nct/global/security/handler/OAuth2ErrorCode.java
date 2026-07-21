package nct.global.security.handler;

// @ai_generated: 작업단위5 - 레드팀 Q4 반영. OAuth2 로그인 실패 시 OAuth2FailureHandler가
// 프론트로 넘기는 ?oauthError= 값의 고정 계약. 이 문자열은
// nct-frontend/.../pages/auth/OAuthRedirectHandler.jsx 의 OAUTH_ERROR_MESSAGES 키와 1:1 대응한다.
// ErrorCode.name()을 직접 갖다 쓰면 그 enum 상수 이름이 바뀔 때 컴파일 에러 없이 조용히
// 매칭이 깨지므로(프론트가 일반 문구로 폴백), 프론트와의 계약을 이 클래스 하나로 고정해 분리한다.
// 값을 바꿀 때는 반드시 OAuthRedirectHandler.jsx의 OAUTH_ERROR_MESSAGES도 함께 수정한다.
/** OAuth2 로그인 실패 코드 — 프론트 OAuthRedirectHandler.jsx와의 고정 계약 */
public final class OAuth2ErrorCode {

    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    public static final String WITHDRAWN_USER = "WITHDRAWN_USER";
    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String DUPLICATE_NICKNAME = "DUPLICATE_NICKNAME";
    public static final String OAUTH_EMAIL_REQUIRED = "OAUTH_EMAIL_REQUIRED";
    public static final String OAUTH_UNSUPPORTED_PROVIDER = "OAUTH_UNSUPPORTED_PROVIDER";
    public static final String OAUTH_LOGIN_FAILED = "OAUTH_LOGIN_FAILED";

    // @ai_generated: 작업단위5 작업 2 - 연동(link) 전용 실패 코드
    /** 연동 콜백 도착 시 로그인 쿠키가 없거나 만료됨 - 폴백 없이 재로그인 안내 */
    public static final String LINK_AUTH_REQUIRED = "LINK_AUTH_REQUIRED";
    /** 연동하려는 제공자 계정이 이미 다른 사용자에게 연동돼 있음 */
    public static final String ALREADY_LINKED_ELSEWHERE = "ALREADY_LINKED_ELSEWHERE";
    /** 이미 본인 계정에 같은 제공자가 연동돼 있음(동일 제공자 재연동 시도) */
    public static final String ALREADY_LINKED_SELF = "ALREADY_LINKED_SELF";

    // @ai_generated: 작업단위5(F-AUTH-004 온보딩, ISS-009/POL-AUTH-015)
    /** 미연동 신규 사용자 - 즉시 가입 대신 온보딩(약관 동의+닉네임 확정) 필요 */
    public static final String ONBOARDING_REQUIRED = "ONBOARDING_REQUIRED";

    private OAuth2ErrorCode() {
    }
}
