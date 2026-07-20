package nct.member.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import nct.auth.domain.EmailVerification;
import nct.auth.mapper.EmailVerificationMapper;
import nct.auth.service.EmailSender;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.utils.TokenHashUtil;
import nct.member.domain.Member;
import nct.member.dto.WithdrawalConfirmRequest;
import nct.member.dto.WithdrawalLinkRequestDto;
import nct.member.mapper.MemberMapper;

// @ai_generated
/** F-AUTH-011: 정지 계정 전용 탈퇴 링크의 계정 상태 비노출(정지만 실제 대상), 재발송 제한,
 *  링크 확정의 PENDING->VERIFIED->USED 상태 전이·공통 탈퇴 위임을 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class MemberWithdrawalRequestServiceTest {

    @Mock
    private EmailVerificationMapper emailVerificationMapper;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private MemberService memberService;
    @Mock
    private EmailSender emailSender;

    private TokenHashUtil tokenHashUtil;
    private MemberWithdrawalRequestService withdrawalRequestService;

    @BeforeEach
    void setUp() {
        tokenHashUtil = new TokenHashUtil();
        withdrawalRequestService = new MemberWithdrawalRequestService(
                emailVerificationMapper, authMemberPort, memberMapper, memberService, emailSender, tokenHashUtil);
    }

    @Test
    void 아이디와_이메일이_일치하는_정지계정에는_링크를_발송한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(suspendedMember()));
        when(emailVerificationMapper.findLatestWithdrawalByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.empty());

        withdrawalRequestService.requestWithdrawal(linkRequest("buyer01", "user@example.com"));

        verify(emailVerificationMapper).insertWithdrawal(any(EmailVerification.class));
        verify(emailSender).sendWithdrawalLink(eq("user@example.com"), anyString());
    }

    @Test
    void 활성_계정은_조용히_무시하고_메일을_보내지_않는다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(memberWithStatus("USRC0001")));

        assertThatCode(() -> withdrawalRequestService.requestWithdrawal(linkRequest("buyer01", "user@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertWithdrawal(any());
        verify(emailSender, never()).sendWithdrawalLink(anyString(), anyString());
    }

    @Test
    void 존재하지_않는_아이디는_조용히_무시한다() {
        when(authMemberPort.findByLoginId("nobody")).thenReturn(Optional.empty());

        assertThatCode(() -> withdrawalRequestService.requestWithdrawal(linkRequest("nobody", "user@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertWithdrawal(any());
        verify(emailSender, never()).sendWithdrawalLink(anyString(), anyString());
    }

    @Test
    void 이메일이_일치하지_않으면_조용히_무시한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(suspendedMember()));

        assertThatCode(() -> withdrawalRequestService.requestWithdrawal(linkRequest("buyer01", "다른@example.com")))
                .doesNotThrowAnyException();

        verify(emailVerificationMapper, never()).insertWithdrawal(any());
        verify(emailSender, never()).sendWithdrawalLink(anyString(), anyString());
    }

    @Test
    void 마지막_발송_1분_이내_재발송은_차단한다() {
        when(authMemberPort.findByLoginId("buyer01")).thenReturn(Optional.of(suspendedMember()));
        EmailVerification pending = EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(LocalDateTime.now().plusHours(1))
                .emlVrfLastSentAt(LocalDateTime.now())
                .emlVrfResendCnt(0)
                .build();
        when(emailVerificationMapper.findLatestWithdrawalByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> withdrawalRequestService.requestWithdrawal(linkRequest("buyer01", "user@example.com")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON);
    }

    @Test
    void 존재하지_않는_토큰은_NOT_FOUND를_던진다() {
        when(emailVerificationMapper.findWithdrawalByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawalRequestService.confirmWithdrawal(confirmRequest("bad-token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);
    }

    @Test
    void 만료된_링크는_EXPIRED로_전환하고_거부한다() {
        EmailVerification expired = pendingVerification(LocalDateTime.now().minusSeconds(1));
        when(emailVerificationMapper.findWithdrawalByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> withdrawalRequestService.confirmWithdrawal(confirmRequest("token")))
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
        when(emailVerificationMapper.findWithdrawalByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> withdrawalRequestService.confirmWithdrawal(confirmRequest("token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
    }

    @Test
    void 발급후_계정이_활성화되면_확정을_거부하고_탈퇴하지_않는다() {
        EmailVerification pending = pendingVerification(LocalDateTime.now().plusHours(1));
        when(emailVerificationMapper.findWithdrawalByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(pending));
        // @ai_generated: 발급 시점엔 정지(USRC0002)였다가 확정 시점엔 활성(USRC0001)으로 바뀐 상황을 재현한다.
        when(memberMapper.findMemberByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(memberDomainWithStatus("USRC0001")));

        assertThatThrownBy(() -> withdrawalRequestService.confirmWithdrawal(confirmRequest("token")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(emailVerificationMapper, never()).markVerified(any(), any());
        verify(emailVerificationMapper, never()).markUsed(any(), any());
        verify(memberService, never()).withdraw(anyLong());
    }

    @Test
    void 유효한_링크는_공통_탈퇴_처리를_위임한다() {
        EmailVerification pending = pendingVerification(LocalDateTime.now().plusHours(1));
        when(emailVerificationMapper.findWithdrawalByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(pending));
        when(emailVerificationMapper.markVerified(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(emailVerificationMapper.markUsed(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(memberMapper.findMemberByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(memberDomainWithStatus("USRC0002")));

        withdrawalRequestService.confirmWithdrawal(confirmRequest("token"));

        verify(emailVerificationMapper).markVerified(eq(1L), any(LocalDateTime.class));
        verify(emailVerificationMapper).markUsed(eq(1L), any(LocalDateTime.class));
        verify(memberService).withdraw(101L);
    }

    private EmailVerification pendingVerification(LocalDateTime expiresAt) {
        return EmailVerification.builder()
                .emlVrfSn(1L)
                .emlVrfEmail("user@example.com")
                .emlVrfStatusCd("EMVC0003")
                .emlVrfExpiresAt(expiresAt)
                .build();
    }

    private AuthMember suspendedMember() {
        return memberWithStatus("USRC0002");
    }

    private AuthMember memberWithStatus(String status) {
        return AuthMember.builder()
                .id(101L).loginId("buyer01").email("user@example.com")
                .name("구매자").nickname("구매자").role("ROLE_USER").status(status).build();
    }

    // @ai_generated: confirmWithdrawal의 잠금 조회(findMemberByEmailForUpdate)는 AuthMember가 아닌
    // 자체 Member 도메인을 반환하므로 별도 헬퍼로 둔다.
    private Member memberDomainWithStatus(String status) {
        return Member.builder()
                .usrSn(101L).usrLoginId("buyer01").usrEml("user@example.com")
                .usrNm("구매자").usrStatusCd(status).build();
    }

    private WithdrawalLinkRequestDto linkRequest(String loginId, String email) {
        WithdrawalLinkRequestDto request = new WithdrawalLinkRequestDto();
        request.setLoginId(loginId);
        request.setEmail(email);
        return request;
    }

    private WithdrawalConfirmRequest confirmRequest(String token) {
        WithdrawalConfirmRequest request = new WithdrawalConfirmRequest();
        request.setToken(token);
        return request;
    }
}
