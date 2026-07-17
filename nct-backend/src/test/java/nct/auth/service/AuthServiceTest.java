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
import nct.auth.dto.SignUpRequest;
import nct.auth.mapper.UserAgreementMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.provider.JwtTokenProvider;
import jakarta.validation.Validation;

// @ai_generated
/** 최종 가입이 인증·필수 동의·동의 이력 저장을 하나의 흐름으로 처리하는지 검증한다. */
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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                passwordEncoder,
                authMemberPort,
                jwtTokenProvider,
                emailVerificationService,
                userAgreementMapper,
                Validation.buildDefaultValidatorFactory().getValidator());
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
