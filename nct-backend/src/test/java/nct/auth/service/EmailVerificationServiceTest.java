package nct.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import nct.auth.domain.EmailVerification;
import nct.auth.dto.EmailVerificationSendRequest;
import nct.auth.dto.EmailVerificationVerifyRequest;
import nct.auth.mapper.EmailVerificationMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMemberPort;

// @ai_generated
/** 인증번호 만료·재발송 대기·오입력 잠금의 핵심 상태 분기를 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationMapper emailVerificationMapper;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private EmailSender emailSender;

    private PasswordEncoder passwordEncoder;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        emailVerificationService = new EmailVerificationService(
                emailVerificationMapper, authMemberPort, passwordEncoder, emailSender);
    }

    @Test
    void 만료된_인증번호는_EXPIRED로_전환하고_검증을_거부한다() {
        EmailVerification verification = pending("123456", LocalDateTime.now().minusSeconds(1), 0);
        when(emailVerificationMapper.findSignupByIdForUpdate(1L)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> emailVerificationService.verifySignupCode(1L, verifyRequest("123456")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_EXPIRED);

        verify(emailVerificationMapper).markExpired(1L);
    }

    @Test
    void 다섯번째_오입력은_LOCKED와_3분_재시도시각을_기록한다() {
        EmailVerification verification = pending("123456", LocalDateTime.now().plusMinutes(3), 4);
        when(emailVerificationMapper.findSignupByIdForUpdate(1L)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> emailVerificationService.verifySignupCode(1L, verifyRequest("654321")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

        verify(emailVerificationMapper).incrementFailure(1L);
        verify(emailVerificationMapper).lock(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 마지막_발송_1분_이내_재발송은_차단한다() {
        EmailVerification verification = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(LocalDateTime.now().plusMinutes(3))
                .emlVrfLastSentAt(LocalDateTime.now())
                .emlVrfResendCnt(0)
                .build();
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        when(emailVerificationMapper.findLatestSignupByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> emailVerificationService.sendSignupCode(sendRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON);
    }

    @Test
    void 재발송을_다섯번_소진하면_대기시간과_무관하게_기존건을_만료하고_새_인증을_생성한다() {
        EmailVerification verification = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(LocalDateTime.now().plusMinutes(3))
                .emlVrfLastSentAt(LocalDateTime.now())
                .emlVrfResendCnt(5)
                .build();
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        when(emailVerificationMapper.findLatestSignupByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(verification));

        emailVerificationService.sendSignupCode(sendRequest());

        verify(emailVerificationMapper).markExpired(1L);
        verify(emailVerificationMapper).insertSignup(any(EmailVerification.class));
        verify(emailSender).sendVerificationCode(eq("user@example.com"), any(String.class));
    }

    @Test
    void 잠금_재시도시각이_지나면_새_인증을_생성한다() {
        EmailVerification verification = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0007")
                .emlVrfExpiresAt(LocalDateTime.now().plusMinutes(3))
                .emlVrfRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(authMemberPort.existsByEmail("user@example.com")).thenReturn(false);
        when(emailVerificationMapper.findLatestSignupByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(verification));

        emailVerificationService.sendSignupCode(sendRequest());

        verify(emailVerificationMapper).markExpired(1L);
        verify(emailVerificationMapper).insertSignup(any(EmailVerification.class));
        verify(emailSender).sendVerificationCode(eq("user@example.com"), any(String.class));
    }

    @Test
    void 일치하는_인증번호는_VERIFIED로_전환한다() {
        EmailVerification verification = pending("123456", LocalDateTime.now().plusMinutes(3), 0);
        when(emailVerificationMapper.findSignupByIdForUpdate(1L)).thenReturn(Optional.of(verification));
        when(emailVerificationMapper.markVerified(eq(1L), any(LocalDateTime.class))).thenReturn(1);

        emailVerificationService.verifySignupCode(1L, verifyRequest("123456"));

        verify(emailVerificationMapper).markVerified(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 이메일이_null이면_입력오류로_처리한다() {
        EmailVerificationSendRequest request = sendRequest();
        request.setEmail(null);

        assertThatThrownBy(() -> emailVerificationService.sendSignupCode(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private EmailVerification pending(String code, LocalDateTime expiresAt, int failCount) {
        return EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfCodeHash(passwordEncoder.encode(code))
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(expiresAt)
                .emlVrfLastSentAt(LocalDateTime.now().minusMinutes(2))
                .emlVrfFailCnt(failCount)
                .build();
    }

    private EmailVerificationVerifyRequest verifyRequest(String code) {
        EmailVerificationVerifyRequest request = new EmailVerificationVerifyRequest();
        request.setCode(code);
        return request;
    }

    private EmailVerificationSendRequest sendRequest() {
        EmailVerificationSendRequest request = new EmailVerificationSendRequest();
        request.setEmail("user@example.com");
        request.setTermsAgreed(true);
        request.setPrivacyAgreed(true);
        return request;
    }
}
