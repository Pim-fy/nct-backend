package nct.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auth.dto.AgreementRequest;
import nct.auth.dto.OauthOnboardingRequest;
import nct.auth.mapper.UserAgreementMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProfile;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.security.provider.OAuthOnboardingTokenProvider;
import nct.global.security.provider.OAuthOnboardingTokenProvider.OnboardingClaims;

// @ai_generated
/** OAuth 온보딩의 선택정보 정규화, 은행·계좌 묶음 검증 및 가입 후속 저장 순서를 검증한다. */
@ExtendWith(MockitoExtension.class)
class OauthOnboardingServiceTest {

    @Mock
    private OAuthOnboardingTokenProvider onboardingTokenProvider;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private UserAgreementMapper userAgreementMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private OauthOnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        onboardingService = new OauthOnboardingService(
                onboardingTokenProvider, authMemberPort, userAgreementMapper, jwtTokenProvider);
    }

    @Test
    void 선택정보_다섯개를_정규화해_가입포트로_전달하고_약관과_토큰을_저장한다() {
        OauthOnboardingRequest request = validRequest();
        request.setTelno(" 01012345678 ");
        request.setAddress(" 서울특별시 종로구 세종대로 1 ");
        request.setDetailAddress(" 101동 1001호 ");
        request.setBankName(" 에누리은행 ");
        request.setAccountNo(" 123-456-789 ");
        AuthMember savedMember = savedMember();
        when(onboardingTokenProvider.parseToken("onboarding-token")).thenReturn(claims());
        when(authMemberPort.registerOAuthMember(any(OAuthProfile.class))).thenReturn(savedMember);
        when(jwtTokenProvider.createAccessToken(501L)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(501L, true)).thenReturn("refresh-token");

        AuthSessionResult result = onboardingService.complete("onboarding-token", request);

        ArgumentCaptor<OAuthProfile> profileCaptor = ArgumentCaptor.forClass(OAuthProfile.class);
        verify(authMemberPort).registerOAuthMember(profileCaptor.capture());
        OAuthProfile profile = profileCaptor.getValue();
        assertThat(profile.getProvider()).isEqualTo("USRC0004");
        assertThat(profile.getProviderKey()).isEqualTo("provider-user-1");
        assertThat(profile.getTelno()).isEqualTo("01012345678");
        assertThat(profile.getAddress()).isEqualTo("서울특별시 종로구 세종대로 1");
        assertThat(profile.getDetailAddress()).isEqualTo("101동 1001호");
        assertThat(profile.getBankName()).isEqualTo("에누리은행");
        assertThat(profile.getAccountNo()).isEqualTo("123-456-789");
        verify(userAgreementMapper).insertAll(any());
        verify(authMemberPort).updateRefreshToken(501L, "refresh-token");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
    }

    // @ai_generated: ISS-023 - 온보딩 전화번호가 선택에서 필수로 전환됐으므로 빈 값은 가입을 차단해야 한다.
    @Test
    void 전화번호가_비어있으면_가입과_약관저장을_시작하지_않는다() {
        OauthOnboardingRequest request = validRequest();
        request.setTelno("");
        when(onboardingTokenProvider.parseToken("onboarding-token")).thenReturn(claims());

        assertThatThrownBy(() -> onboardingService.complete("onboarding-token", request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(authMemberPort, never()).registerOAuthMember(any());
        verify(userAgreementMapper, never()).insertAll(any());
        verify(authMemberPort, never()).updateRefreshToken(any(), any());
    }

    @Test
    void 은행명과_계좌번호가_한쪽만_입력되면_가입과_약관저장을_시작하지_않는다() {
        OauthOnboardingRequest request = validRequest();
        request.setBankName("에누리은행");
        when(onboardingTokenProvider.parseToken("onboarding-token")).thenReturn(claims());

        assertThatThrownBy(() -> onboardingService.complete("onboarding-token", request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(authMemberPort, never()).registerOAuthMember(any());
        verify(userAgreementMapper, never()).insertAll(any());
        verify(authMemberPort, never()).updateRefreshToken(any(), any());
    }

    private OauthOnboardingRequest validRequest() {
        OauthOnboardingRequest request = new OauthOnboardingRequest();
        request.setNickname("온보딩회원");
        request.setTelno("01012345678");
        request.setAgreements(List.of(
                agreement("AGRC0001", true), agreement("AGRC0002", true), agreement("AGRC0003", false)));
        return request;
    }

    private AgreementRequest agreement(String code, boolean agreed) {
        AgreementRequest request = new AgreementRequest();
        request.setAgreementTypeCode(code);
        request.setAgreed(agreed);
        return request;
    }

    private OnboardingClaims claims() {
        return new OnboardingClaims("USRC0004", "provider-user-1", "oauth@example.com", "기본닉네임");
    }

    private AuthMember savedMember() {
        return AuthMember.builder()
                .id(501L).email("oauth@example.com").name("온보딩회원")
                .nickname("온보딩회원").role("ROLE_USER").build();
    }
}
