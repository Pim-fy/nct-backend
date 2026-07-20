package nct.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import nct.auth.dto.PasswordResetConfirmRequest;
import nct.auth.dto.PasswordResetRequestDto;
import nct.auth.mapper.EmailVerificationMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.utils.TokenHashUtil;

// @ai_generated
/** F-AUTH-007: 계정 상태 비노출(정지·탈퇴·불일치는 조용히 무시), 재발송 제한, 링크 확정의
 *  PENDING->VERIFIED->USED 상태 전이·세션 무효화를 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private EmailVerificationMapper emailVerificationMapper;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private EmailSender emailSender;

    private PasswordEncoder passwordEncoder;
    private TokenHashUtil tokenHashUtil;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        tokenHashUtil = new TokenHashUtil();
        passwordResetService = new PasswordResetService(
                emailVerificationMapper, authMemberPort, passwordEncoder, emailSender, tokenHashUtil);
    }

    @Test
    void 아이디와_이메일이_일치하는_활성계정에는_링크를_발송한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(activeMember()));
        when(emailVerificationMapper.findLatestPasswordResetByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.empty());

        passwordResetService.requestReset(resetRequest("buyer01", "user@example.com"));

        verify(emailVerificationMapper).insertPasswordReset(any(EmailVerification.class));
        verify(emailSender).sendPasswordResetLink(eq("user@example.com"), anyString());
    }

    @Test
    void 존재하지_않는_아이디는_조용히_무시하고_메일을_보내지_않는다() {
        when(authMemberPort.findByLoginId("nobody")).thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestReset(resetRequest("nobody", "user@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertPasswordReset(any());
        verify(emailSender, never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void 이메일이_일치하지_않으면_조용히_무시한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(activeMember()));

        assertThatCode(() -> passwordResetService.requestReset(resetRequest("buyer01", "다른@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertPasswordReset(any());
        verify(emailSender, never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void 정지된_계정은_조용히_무시한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(memberWithStatus("USRC0002")));

        assertThatCode(() -> passwordResetService.requestReset(resetRequest("buyer01", "user@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertPasswordReset(any());
        verify(emailSender, never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void 탈퇴한_계정은_조용히_무시한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(memberWithStatus("USRC0003")));

        assertThatCode(() -> passwordResetService.requestReset(resetRequest("buyer01", "user@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertPasswordReset(any());
        verify(emailSender, never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void 마지막_발송_1분_이내_재발송은_차단한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(activeMember()));
        EmailVerification pending = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(LocalDateTime.now().plusHours(1))
                .emlVrfLastSentAt(LocalDateTime.now())
                .emlVrfResendCnt(0)
                .build();
        when(emailVerificationMapper.findLatestPasswordResetByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> passwordResetService.requestReset(resetRequest("buyer01", "user@example.com")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON);
    }

    @Test
    void 재발송을_다섯번_소진하면_대기시간과_무관하게_기존건을_만료하고_새로_발급한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(activeMember()));
        EmailVerification pending = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(LocalDateTime.now().plusHours(1))
                .emlVrfLastSentAt(LocalDateTime.now())
                .emlVrfResendCnt(5)
                .build();
        when(emailVerificationMapper.findLatestPasswordResetByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(pending));

        passwordResetService.requestReset(resetRequest("buyer01", "user@example.com"));

        verify(emailVerificationMapper).markExpired(1L);
        verify(emailVerificationMapper).insertPasswordReset(any(EmailVerification.class));
    }

    @Test
    void 존재하지_않는_토큰은_NOT_FOUND를_던진다() {
        when(emailVerificationMapper.findPasswordResetByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirmReset(confirmRequest("bad-token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);
    }

    @Test
    void 만료된_링크는_EXPIRED로_전환하고_거부한다() {
        EmailVerification expired = pendingVerification(LocalDateTime.now().minusSeconds(1));
        when(emailVerificationMapper.findPasswordResetByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.confirmReset(confirmRequest("token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_EXPIRED);

        verify(emailVerificationMapper).markExpired(1L);
    }

    @Test
    void 이미_사용된_링크는_ALREADY_USED를_던진다() {
        EmailVerification used = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0005")
                .emlVrfExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(emailVerificationMapper.findPasswordResetByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> passwordResetService.confirmReset(confirmRequest("token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
    }

    @Test
    void 유효한_링크는_비밀번호를_변경하고_모든_세션을_무효화한다() {
        EmailVerification pending = pendingVerification(LocalDateTime.now().plusHours(1));
        when(emailVerificationMapper.findPasswordResetByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(pending));
        when(emailVerificationMapper.markVerified(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(emailVerificationMapper.markUsed(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(authMemberPort.findByEmail("user@example.com")).thenReturn(Optional.of(activeMember()));

        passwordResetService.confirmReset(confirmRequest("token"));

        verify(emailVerificationMapper).markVerified(eq(1L), any(LocalDateTime.class));
        verify(emailVerificationMapper).markUsed(eq(1L), any(LocalDateTime.class));
        verify(authMemberPort).updatePassword(eq(101L), anyString());
        verify(authMemberPort).updateRefreshToken(101L, null);
    }

    private EmailVerification pendingVerification(LocalDateTime expiresAt) {
        return EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(expiresAt)
                .build();
    }

    private AuthMember activeMember() {
        return AuthMember.builder()
                .id(101L).loginId("buyer01").email("user@example.com")
                .name("구매자").nickname("구매자").role("ROLE_USER").status("USRC0001").build();
    }

    private AuthMember memberWithStatus(String status) {
        return AuthMember.builder()
                .id(101L).loginId("buyer01").email("user@example.com")
                .name("구매자").nickname("구매자").role("ROLE_USER").status(status).build();
    }

    private PasswordResetRequestDto resetRequest(String loginId, String email) {
        PasswordResetRequestDto request = new PasswordResetRequestDto();
        request.setLoginId(loginId);
        request.setEmail(email);
        return request;
    }

    private PasswordResetConfirmRequest confirmRequest(String token) {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setToken(token);
        request.setNewPassword("NewPassword1!");
        return request;
    }
}
