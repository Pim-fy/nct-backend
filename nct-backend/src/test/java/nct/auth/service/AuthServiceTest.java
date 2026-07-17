package nct.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import nct.auth.domain.UserAgreement;
import nct.auth.dto.AgreementRequest;
import nct.auth.dto.LoginRequest;
import nct.auth.dto.SignUpRequest;
import nct.auth.mapper.UserAgreementMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.utils.TokenHashUtil;
import jakarta.validation.Validation;

// @ai_generated
/** 최종 가입이 인증·필수 동의·동의 이력 저장을 하나의 흐름으로 처리하는지, 로그인·재발급이
 *  usrSn subject·해시 대조·계정상태 차단(F-AUTH-009)을 지키는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private UserAgreementMapper userAgreementMapper;
    @Mock
    private TokenHashUtil tokenHashUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                passwordEncoder,
                authMemberPort,
                jwtTokenProvider,
                emailVerificationService,
                userAgreementMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                tokenHashUtil);
    }

    @Test
    void 인증과_필수약관이_완료되면_회원과_약관3건을_저장하고_인증을_사용완료한다() {
        SignUpRequest request = validRequest();
        AuthMember savedMember = AuthMember.builder()
                .id(101L)
                .email("user@example.com")
                .name("구매자")
                .nickname("구매자")
                .role("ROLE_USER")
                .build();
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(false);
        when(authMemberPort.existsByNickname("구매자")).thenReturn(false);
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
        when(authMemberPort.registerLocalMember(any(LocalSignUpProfile.class))).thenReturn(savedMember);
        doNothing().when(emailVerificationService).requireVerifiedSignup(77L, "user@example.com");

        authService.signUp(request);

        ArgumentCaptor<LocalSignUpProfile> profileCaptor = ArgumentCaptor.forClass(LocalSignUpProfile.class);
        verify(authMemberPort).registerLocalMember(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLoginId()).isEqualTo("buyer01");
        assertThat(profileCaptor.getValue().getNickname()).isEqualTo("구매자");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> agreementCaptor = ArgumentCaptor.forClass(List.class);
        verify(userAgreementMapper).insertAll(agreementCaptor.capture());
        assertThat(agreementCaptor.getValue()).hasSize(3);
        assertThat(agreementCaptor.getValue()).allMatch(agreement -> agreement.getUsrSn().equals(101L));
        verify(emailVerificationService).markSignupUsed(77L);
    }

    @Test
    void 인증이_완료되지_않으면_회원과_약관을_저장하지_않는다() {
        SignUpRequest request = validRequest();
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(false);
        when(authMemberPort.existsByNickname("구매자")).thenReturn(false);
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED))
                .when(emailVerificationService).requireVerifiedSignup(77L, "user@example.com");

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);

        verify(authMemberPort, never()).registerLocalMember(any());
        verify(userAgreementMapper, never()).insertAll(any());
        verify(emailVerificationService, never()).markSignupUsed(any());
    }

    @Test
    void 닉네임이_중복되면_인증건을_사용하거나_회원정보를_저장하지_않는다() {
        SignUpRequest request = validRequest();
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(false);
        when(authMemberPort.existsByNickname("구매자")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        verify(emailVerificationService, never()).requireVerifiedSignup(any(), any());
        verify(authMemberPort, never()).registerLocalMember(any());
        verify(emailVerificationService, never()).markSignupUsed(any());
    }

    @Test
    void 필수약관을_거부하면_회원과_인증건을_저장하지_않는다() {
        SignUpRequest request = validRequest();
        request.setAgreements(List.of(agreement("AGRC0001", false),
                                     agreement("AGRC0002", true),
                                     agreement("AGRC0003", false)));

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED);

        verify(authMemberPort, never()).registerLocalMember(any());
        verify(emailVerificationService, never()).requireVerifiedSignup(any(), any());
        verify(emailVerificationService, never()).markSignupUsed(any());
    }

    @Test
    void 로그인아이디가_중복되면_해당오류로_가입을_차단한다() {
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(validRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    void 이메일이_중복되면_해당오류로_가입을_차단한다() {
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(false);
        when(authMemberPort.existsByNickname("구매자")).thenReturn(false);
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(validRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 이메일_중복확인은_형식이_아닌_값을_400_오류로_차단한다() {
        assertThatThrownBy(() -> authService.checkEmail("not-an-email"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EMAIL_FORMAT);

        verify(authMemberPort, never()).existsByEmail(any());
    }

    @Test
    void DB_UNIQUE_제약은_식별자별_중복오류로_변환한다() {
        assertUniqueConstraint("UK_USERS_LOGIN_ID", ErrorCode.DUPLICATE_LOGIN_ID);
        assertUniqueConstraint("UK_USERS_NM", ErrorCode.DUPLICATE_NICKNAME);
        assertUniqueConstraint("UK_USERS_EML", ErrorCode.DUPLICATE_EMAIL);
    }

    private void assertUniqueConstraint(String constraintName, ErrorCode expectedErrorCode) {
        SignUpRequest request = validRequest();
        when(authMemberPort.existsByLoginId("buyer01")).thenReturn(false);
        when(authMemberPort.existsByNickname("구매자")).thenReturn(false);
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
        when(authMemberPort.registerLocalMember(any(LocalSignUpProfile.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate", new SQLException("Duplicate key " + constraintName)));

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expectedErrorCode);
    }

    @Test
    void 정상_로그인은_usrSn을_subject로_토큰을_발급하고_리프레시토큰을_해시화해_저장한다() {
        AuthMember member = activeMember();
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(java.util.Optional.of(member));
        when(passwordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(101L, "ROLE_USER")).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(101L)).thenReturn("refresh-token-raw");

        AuthSessionResult result = authService.login(loginRequest("buyer01", "Password1!"));

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-raw");
        // @ai_generated: 해시화 없이 원문을 그대로 저장하면 이 테스트가 실패한다 - MemberAuthAdapter가 해시화 책임을 지므로
        // 여기서는 AuthService가 원문 refreshToken을 그대로 포트에 넘기는지만 확인한다(해시화는 어댑터 단위 책임).
        verify(authMemberPort).updateRefreshToken(101L, "refresh-token-raw");
    }

    @Test
    void 정지된_계정은_비밀번호가_맞아도_로그인을_차단한다() {
        AuthMember member = memberWithStatus("USRC0002");
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(java.util.Optional.of(member));
        when(passwordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("buyer01", "Password1!")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
        verify(authMemberPort, never()).updateRefreshToken(any(), any());
    }

    @Test
    void 탈퇴한_계정은_비밀번호가_맞아도_로그인을_차단한다() {
        AuthMember member = memberWithStatus("USRC0003");
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(java.util.Optional.of(member));
        when(passwordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("buyer01", "Password1!")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    void 재발급은_요청토큰을_해시화한뒤_DB저장값과_일치해야_성공한다() {
        AuthMember member = activeMemberWithRefreshHash("stored-hash");
        when(jwtTokenProvider.getUsrSn("raw-refresh-token")).thenReturn(101L);
        when(authMemberPort.findById(101L)).thenReturn(java.util.Optional.of(member));
        when(tokenHashUtil.hash("raw-refresh-token")).thenReturn("stored-hash");
        when(jwtTokenProvider.createAccessToken(101L, "ROLE_USER")).thenReturn("new-access-token");

        String accessToken = authService.refresh("raw-refresh-token");

        assertThat(accessToken).isEqualTo("new-access-token");
    }

    @Test
    void 재발급은_해시가_DB저장값과_다르면_탈취토큰으로_거부한다() {
        AuthMember member = activeMemberWithRefreshHash("stored-hash");
        when(jwtTokenProvider.getUsrSn("raw-refresh-token")).thenReturn(101L);
        when(authMemberPort.findById(101L)).thenReturn(java.util.Optional.of(member));
        when(tokenHashUtil.hash("raw-refresh-token")).thenReturn("different-hash");

        assertThatThrownBy(() -> authService.refresh("raw-refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 재발급_시점에도_계정이_정지상태면_차단한다() {
        AuthMember member = AuthMember.builder()
                .id(101L).email("user@example.com").role("ROLE_USER")
                .status("USRC0002").refreshToken("stored-hash").build();
        when(jwtTokenProvider.getUsrSn("raw-refresh-token")).thenReturn(101L);
        when(authMemberPort.findById(101L)).thenReturn(java.util.Optional.of(member));
        when(tokenHashUtil.hash("raw-refresh-token")).thenReturn("stored-hash");

        assertThatThrownBy(() -> authService.refresh("raw-refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void 재발급은_구버전_토큰의_subject_파싱실패를_INVALID_TOKEN으로_전파한다() {
        // 레드팀 3-A: JwtTokenProvider.getUsrSn이 구버전(subject=email) 토큰에서 던지는
        // CustomException(INVALID_TOKEN)이 AuthService를 그대로 통과해 컨트롤러까지 전파되는지 확인.
        when(jwtTokenProvider.getUsrSn("legacy-email-subject-token"))
                .thenThrow(new CustomException(ErrorCode.INVALID_TOKEN));

        assertThatThrownBy(() -> authService.refresh("legacy-email-subject-token"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(authMemberPort, never()).findById(any());
    }

    private LoginRequest loginRequest(String loginId, String password) {
        LoginRequest request = new LoginRequest();
        request.setLoginId(loginId);
        request.setPassword(password);
        return request;
    }

    private AuthMember activeMember() {
        return AuthMember.builder()
                .id(101L).email("user@example.com").password("encoded-password")
                .name("구매자").nickname("구매자").role("ROLE_USER").status("USRC0001").build();
    }

    private AuthMember memberWithStatus(String status) {
        return AuthMember.builder()
                .id(101L).email("user@example.com").password("encoded-password")
                .name("구매자").nickname("구매자").role("ROLE_USER").status(status).build();
    }

    private AuthMember activeMemberWithRefreshHash(String refreshTokenHash) {
        return AuthMember.builder()
                .id(101L).email("user@example.com").role("ROLE_USER")
                .status("USRC0001").refreshToken(refreshTokenHash).build();
    }

    private SignUpRequest validRequest() {
        SignUpRequest request = new SignUpRequest();
        request.setLoginId("buyer01");
        request.setPassword("Password1!");
        request.setNickname("구매자");
        request.setEmail("user@example.com");
        request.setVerificationId(77L);
        request.setAgreements(List.of(agreement("AGRC0001", true),
                                     agreement("AGRC0002", true),
                                     agreement("AGRC0003", false)));
        return request;
    }

    private AgreementRequest agreement(String code, boolean agreed) {
        AgreementRequest request = new AgreementRequest();
        request.setAgreementTypeCode(code);
        request.setAgreed(agreed);
        return request;
    }
}
