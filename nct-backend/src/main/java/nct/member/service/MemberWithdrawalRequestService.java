package nct.member.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import lombok.RequiredArgsConstructor;

// @ai_generated
/**
 * F-AUTH-011: 정지 계정 전용 탈퇴 확인 링크의 생성·재발송·확정을 담당한다.
 * 정지 상태는 로그인이 차단돼(F-AUTH-009) 마이페이지의 활성 계정 경로(비밀번호 재확인)를 쓸 수
 * 없으므로, PasswordResetService(작업단위3)와 동일한 이메일 링크 메커니즘을 목적코드만 바꿔 그대로
 * 재사용한다(오입력 잠금 없음, 링크 클릭이 곧 확정).
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalRequestService {

    private static final String WITHDRAWAL_PURPOSE = "EMVC0008";
    private static final String PENDING = "EMVC0003";
    private static final String VERIFIED = "EMVC0004";
    private static final String USED = "EMVC0005";
    private static final String EXPIRED = "EMVC0006";
    private static final String STATUS_SUSPENDED = "USRC0002";
    private static final int MAX_RESEND_COUNT = 5;
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationMapper emailVerificationMapper;
    private final AuthMemberPort authMemberPort;
    private final MemberMapper memberMapper;
    private final MemberService memberService;
    private final EmailSender emailSender;
    private final TokenHashUtil tokenHashUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * 탈퇴 확인 링크 발송(재발송 포함).
     * 계정 미존재·이메일 불일치·활성·탈퇴 전부 동일하게 "발송 완료"로 응답하되,
     * 이 경우 실제로는 토큰 생성·저장·메일 발송을 전혀 수행하지 않는다(계정 상태 비노출).
     * 이 경로는 오직 정지(USRC0002) 상태 계정만 실제 대상이다 - 활성 계정은 로그인 상태에서
     * MemberService.withdrawActive로 처리하고, 이 엔드포인트를 쓸 필요가 없다.
     */
    @Transactional
    public void requestWithdrawal(WithdrawalLinkRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        String loginId = requireText(request.getLoginId());

        if (!isEligible(loginId, email)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerification latest = emailVerificationMapper.findLatestWithdrawalByEmailForUpdate(email)
                                                            .orElse(null);
        LocalDateTime expiresAt = now.plusHours(1);

        if (latest == null || shouldCreateNew(latest, now)) {
            if (latest != null) {
                emailVerificationMapper.markExpired(latest.getEmlVrfSn());
            }
            createAndSend(email, now, expiresAt);
            return;
        }

        if (latest.getEmlVrfResendCnt() >= MAX_RESEND_COUNT) {
            emailVerificationMapper.markExpired(latest.getEmlVrfSn());
            createAndSend(email, now, expiresAt);
            return;
        }
        if (latest.getEmlVrfLastSentAt().plusMinutes(1).isAfter(now)) {
            // @ai_generated: 기본 메시지는 "인증번호"를 언급해 링크 방식과 어색하므로 문구를 덮어쓴다.
            throw new CustomException(ErrorCode.EMAIL_RESEND_TOO_SOON, "탈퇴 확인 링크 재발송은 1분 후에 가능합니다.");
        }

        String token = createToken();
        emailVerificationMapper.resend(latest.getEmlVrfSn(), tokenHashUtil.hash(token), expiresAt, now);
        emailSender.sendWithdrawalLink(email, buildLink(token));
    }

    /** 링크 토큰을 검증하고 탈퇴를 확정한다. 성공 시 MemberService.withdraw(공통 처리)를 그대로 위임한다. */
    // @ai_generated: PasswordResetService.confirmReset과 동일 이유로 noRollbackFor 필요
    // (ensureConfirmable의 markExpired 직후 CustomException을 던지면 기본 @Transactional이 그 write까지 롤백한다).
    @Transactional(noRollbackFor = CustomException.class)
    public void confirmWithdrawal(WithdrawalConfirmRequest request) {
        String tokenHash = tokenHashUtil.hash(request.getToken());
        EmailVerification verification = emailVerificationMapper.findWithdrawalByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        ensureConfirmable(verification, now);

        // @ai_generated: Evaluator 지적(P-0719-03) - isEligible은 "발급 시점"에만 정지 상태를 확인한다.
        // 링크 발급 이후 확정 전에 관리자가 계정을 활성화하면, 재검사 없이는 활성 계정에 대해 비밀번호
        // 재확인 없이 탈퇴가 실행되는 우회 경로가 생긴다. 확정 직전에 다시 검사한다.
        // @ai_generated: 레드팀 지적(P-0719-07) - 재검사(확인)와 withdraw() 실행(사용) 사이에도 상태가
        // 바뀔 수 있는 TOCTOU 창이 남는다. 공유 withdraw()의 UPDATE에 상태조건을 넣으면 활성 탈퇴 경로가
        // 깨지므로(같은 UPDATE를 공유), 이 확정 경로 전용으로 SELECT ... FOR UPDATE 잠금 조회를 써서
        // 조회 시점부터 withdraw() 완료(트랜잭션 커밋)까지 해당 회원 행을 잠근다.
        Member member = memberMapper.findMemberByEmailForUpdate(verification.getEmlVrfEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!STATUS_SUSPENDED.equals(member.getUsrStatusCd())) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        // @ai_generated: 링크 방식은 별도 "검증" 단계 없이 클릭이 곧 확정이므로,
        // 기존 SIGNUP/PASSWORD_RESET과 동일한 제네릭 매퍼(PENDING->VERIFIED->USED)를 한 트랜잭션에서 연속 호출한다.
        if (emailVerificationMapper.markVerified(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (emailVerificationMapper.markUsed(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        memberService.withdraw(member.getUsrSn());
    }

    /** 로그인ID+이메일이 일치하고 정지 상태인 계정만 발송 대상이다(활성 계정은 이 경로를 쓰지 않는다). */
    private boolean isEligible(String loginId, String email) {
        AuthMember member = authMemberPort.findByLoginId(loginId).orElse(null);
        return member != null
                && email.equalsIgnoreCase(member.getEmail())
                && STATUS_SUSPENDED.equals(member.getStatus());
    }

    private void createAndSend(String email, LocalDateTime sentAt, LocalDateTime expiresAt) {
        String token = createToken();
        EmailVerification verification = EmailVerification.builder()
                .emlVrfEmail(email)
                .emlVrfPurposeCd(WITHDRAWAL_PURPOSE)
                .emlVrfCodeHash(tokenHashUtil.hash(token))
                .emlVrfStatusCd(PENDING)
                .emlVrfExpiresAt(expiresAt)
                .emlVrfLastSentAt(sentAt)
                .build();
        emailVerificationMapper.insertWithdrawal(verification);
        emailSender.sendWithdrawalLink(email, buildLink(token));
    }

    // @ai_generated: 오입력 잠금(LOCKED) 개념이 없는 링크 방식 - PENDING 만료·EXPIRED·USED·VERIFIED만 새 발급 대상이다.
    private boolean shouldCreateNew(EmailVerification verification, LocalDateTime now) {
        if (PENDING.equals(verification.getEmlVrfStatusCd()) && !verification.getEmlVrfExpiresAt().isAfter(now)) {
            return true;
        }
        return EXPIRED.equals(verification.getEmlVrfStatusCd())
                || USED.equals(verification.getEmlVrfStatusCd())
                || VERIFIED.equals(verification.getEmlVrfStatusCd());
    }

    private void ensureConfirmable(EmailVerification verification, LocalDateTime now) {
        if (USED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
        }
        if (EXPIRED.equals(verification.getEmlVrfStatusCd()) || !verification.getEmlVrfExpiresAt().isAfter(now)) {
            emailVerificationMapper.markExpired(verification.getEmlVrfSn());
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (!PENDING.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
    }

    // @ai_generated: SecureRandom 32바이트를 URL-safe Base64(패딩 없음)로 인코딩 - 그대로 쿼리파라미터에 쓸 수 있다.
    private String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildLink(String token) {
        return frontendUrl + "/withdrawal?token=" + token;
    }

    private String normalizeEmail(String email) {
        return requireText(email).toLowerCase(java.util.Locale.ROOT);
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value.trim();
    }
}
