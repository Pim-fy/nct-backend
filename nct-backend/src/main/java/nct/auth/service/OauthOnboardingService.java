package nct.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auth.dto.LoginResponse;
import nct.auth.dto.OauthOnboardingPendingResponse;
import nct.auth.dto.OauthOnboardingRequest;
import nct.auth.mapper.UserAgreementMapper;
import nct.auth.util.AgreementValidator;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProfile;
import nct.global.security.port.OAuthProviderParser;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.security.provider.OAuthOnboardingTokenProvider;
import nct.global.security.provider.OAuthOnboardingTokenProvider.OnboardingClaims;

/**
 * [소셜 최초 가입 온보딩 완료]
 * - 작업단위5(F-AUTH-004 온보딩, ISS-009/POL-AUTH-015) - CustomOAuth2UserService가 더 이상
 *   즉시 가입시키지 않는 대신, 이 서비스가 온보딩 제출 시점에 계정을 생성한다.
 * - AuthMemberPort.registerOAuthMember(작업 1, 검증 완료)를 그대로 재사용하고 UserAgreementMapper
 *   (AuthService.signUp과 동일)를 이어서 호출하는 조합으로 처리한다 - 포트 인터페이스는 미변경.
 */
@Service
@RequiredArgsConstructor
public class OauthOnboardingService {

    private final OAuthOnboardingTokenProvider onboardingTokenProvider;
    private final AuthMemberPort authMemberPort;
    private final UserAgreementMapper userAgreementMapper;
    private final JwtTokenProvider jwtTokenProvider;

    /** 온보딩 화면 진입 시 닉네임 기본값 등을 내려주기 위한 조회 - DB 접근 없이 토큰만 검증한다. */
    @Transactional(readOnly = true)
    public OauthOnboardingPendingResponse getPending(String onboardingToken) {
        if (onboardingToken == null) {
            throw new CustomException(ErrorCode.ONBOARDING_TOKEN_NOT_FOUND);
        }
        OnboardingClaims claims = onboardingTokenProvider.parseToken(onboardingToken);
        return OauthOnboardingPendingResponse.builder()
                                             .nickname(claims.nickname())
                                             .provider(OAuthProviderParser.providerCdToFriendlyKey(claims.providerCd()))
                                             .build();
    }

    @Transactional
    public AuthSessionResult complete(String onboardingToken, OauthOnboardingRequest request) {
        if (onboardingToken == null) {
            throw new CustomException(ErrorCode.ONBOARDING_TOKEN_NOT_FOUND);
        }
        OnboardingClaims claims = onboardingTokenProvider.parseToken(onboardingToken);

        AgreementValidator.validateAgreementSet(request.getAgreements());

        String nickname = request.getNickname().trim();

        AuthMember member;
        try {
            member = authMemberPort.registerOAuthMember(
                    OAuthProfile.builder()
                                .provider(claims.providerCd())
                                .providerKey(claims.providerKey())
                                .email(claims.email())
                                .nickname(nickname)
                                .build());
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(ex);
        }

        userAgreementMapper.insertAll(AgreementValidator.toUserAgreements(member.getId(), request.getAgreements()));

        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        authMemberPort.updateRefreshToken(member.getId(), refreshToken);

        return AuthSessionResult.builder()
                                .loginResponse(toLoginResponse(member))
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .build();
    }

    // @ai_generated: AuthService.duplicateException·MemberService.duplicateException과 동일 패턴
    // (이 프로젝트에서 이미 3곳에 독립적으로 존재하는 관례 - 여기서도 그대로 따른다).
    private CustomException duplicateException(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("UK_USERS_EML")) {
            return new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (message.contains("UK_USERS_NM")) {
            return new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return new CustomException(ErrorCode.CONFLICT);
    }

    private LoginResponse toLoginResponse(AuthMember member) {
        return LoginResponse.builder()
                            .id(member.getId())
                            .email(member.getEmail())
                            .name(member.getName())
                            .nickname(member.getNickname())
                            .role(member.getRole())
                            .provider(member.getProvider())
                            .build();
    }
}
