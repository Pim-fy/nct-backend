package nct.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auth.domain.EmailVerification;
import nct.auth.dto.PasswordResetConfirmRequest;
import nct.auth.dto.PasswordResetRequestDto;
import nct.auth.mapper.EmailVerificationMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.utils.TokenHashUtil;
import lombok.RequiredArgsConstructor;

// @ai_generated
/**
 * F-AUTH-007: 비밀번호 재설정 링크의 생성·재발송·확정을 담당한다.
 * EmailVerificationService(SIGNUP 6자리 코드 검증)와는 검증 메커니즘이 근본적으로 달라
 * (오입력 잠금 없음, 링크 클릭+새 비밀번호 제출이 한 번에 확정) 별도 클래스로 분리했다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PASSWORD_RESET_PURPOSE = "EMVC0002";
    private static final String PENDING = "EMVC0003";
    private static final String VERIFIED = "EMVC0004";
    private static final String USED = "EMVC0005";
    private static final String EXPIRED = "EMVC0006";
    private static final String STATUS_ACTIVE = "USRC0001";
    private static final int MAX_RESEND_COUNT = 5;
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationMapper emailVerificationMapper;
    private final AuthMemberPort authMemberPort;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TokenHashUtil tokenHashUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * 재설정 링크 발송(재발송 포함).
     * 계정 미존재·이메일 불일치·정지·탈퇴 전부 동일하게 "발송 완료"로 응답하되,
     * 이 경우 실제로는 토큰 생성·저장·메일 발송을 전혀 수행하지 않는다(계정 상태 비노출).
     */
    @Transactional
    public void requestReset(PasswordResetRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        String loginId = requireText(request.getLoginId());

        if (!isEligible(loginId, email)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerification latest = emailVerificationMapper.findLatestPasswordResetByEmailForUpdate(email)
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
            throw new CustomException(ErrorCode.EMAIL_RESEND_TOO_SOON, "재설정 링크 재발송은 1분 후에 가능합니다.");
        }

        String token = createToken();
        emailVerificationMapper.resend(latest.getEmlVrfSn(), tokenHashUtil.hash(token), expiresAt, now);
        emailSender.sendPasswordResetLink(email, buildLink(token));
    }

    /** 링크 토큰과 새 비밀번호를 함께 검증·확정한다. 성공 시 비밀번호 변경 + 전 세션 무효화. */
    // @ai_generated: 레드팀 지적 - ensureConfirmable의 markExpired 직후 CustomException을 던지면
    // 기본 @Transactional이 그 write까지 롤백해 EXPIRED 전이가 유실된다. EmailVerificationService의
    // 동일 패턴(verifySignupCode)과 같이 CustomException만 롤백에서 제외한다.
    @Transactional(noRollbackFor = CustomException.class)
    public void confirmReset(PasswordResetConfirmRequest request) {
        String tokenHash = tokenHashUtil.hash(request.getToken());
        EmailVerification verification = emailVerificationMapper.findPasswordResetByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        ensureConfirmable(verification, now);

        AuthMember member = authMemberPort.findByEmail(verification.getEmlVrfEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // @ai_generated: 링크 방식은 별도 "검증" 단계 없이 클릭+제출이 한 번에 오므로,
        // 기존 SIGNUP과 동일한 제네릭 매퍼(PENDING->VERIFIED->USED)를 한 트랜잭션에서 연속 호출한다.
        if (emailVerificationMapper.markVerified(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (emailVerificationMapper.markUsed(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        authMemberPort.updatePassword(member.getId(), passwordEncoder.encode(request.getNewPassword()));
        // @ai_generated: F-AUTH-007 - 재설정 성공 시 기존 세션 전부 강제 로그아웃(logout과 동일 패턴 재사용)
        authMemberPort.updateRefreshToken(member.getId(), null);
    }

    /** 로그인ID+이메일이 일치하고 활성 상태인 계정만 발송 대상이다. */
    private boolean isEligible(String loginId, String email) {
        AuthMember member = authMemberPort.findByLoginId(loginId).orElse(null);
        return member != null
                && email.equalsIgnoreCase(member.getEmail())
                && STATUS_ACTIVE.equals(member.getStatus());
    }

    private void createAndSend(String email, LocalDateTime sentAt, LocalDateTime expiresAt) {
        String token = createToken();
        EmailVerification verification = EmailVerification.builder()
                .emlVrfEmail(email)
                .emlVrfPurposeCd(PASSWORD_RESET_PURPOSE)
                .emlVrfCodeHash(tokenHashUtil.hash(token))
                .emlVrfStatusCd(PENDING)
                .emlVrfExpiresAt(expiresAt)
                .emlVrfLastSentAt(sentAt)
                .build();
        emailVerificationMapper.insertPasswordReset(verification);
        emailSender.sendPasswordResetLink(email, buildLink(token));
    }

    // @ai_generated: 오입력 잠금(LOCKED) 개념이 없는 링크 방식이라 EmailVerificationService의
    // shouldCreateNew보다 단순하다 - PENDING 만료·EXPIRED·USED·VERIFIED만 새 발급 대상이다.
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
        return frontendUrl + "/reset-password?token=" + token;
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
