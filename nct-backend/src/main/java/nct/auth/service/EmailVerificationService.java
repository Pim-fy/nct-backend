package nct.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auth.domain.EmailVerification;
import nct.auth.dto.EmailVerificationSendRequest;
import nct.auth.dto.EmailVerificationSendResponse;
import nct.auth.dto.EmailVerificationVerifyRequest;
import nct.auth.dto.EmailVerificationVerifyResponse;
import nct.auth.mapper.EmailVerificationMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.crypto.FieldCryptoService;
import lombok.RequiredArgsConstructor;

// @ai_generated
/** SIGNUP 인증번호의 생성·재발송·검증·잠금·사용완료 상태 전이를 담당한다. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String SIGNUP_PURPOSE = "EMVC0001";
    private static final String PENDING = "EMVC0003";
    private static final String VERIFIED = "EMVC0004";
    private static final String USED = "EMVC0005";
    private static final String EXPIRED = "EMVC0006";
    private static final String LOCKED = "EMVC0007";
    private static final int MAX_RESEND_COUNT = 5;
    private static final int MAX_FAILURE_COUNT = 5;
    // @ai_generated: 인증번호 자체의 유효시간(3분)과 별개로, 인증 성공 후 최종 가입을 마칠 수 있는 유예시간.
    private static final int SIGNUP_GRACE_MINUTES = 60;

    private final EmailVerificationMapper emailVerificationMapper;
    private final AuthMemberPort authMemberPort;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    // @ai_generated: 인증 이력도 원문 이메일 대신 암호문 + HMAC 조회 키를 저장한다.
    private final FieldCryptoService fieldCryptoService;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 필수 약관 동의 뒤에만 SIGNUP 인증번호를 생성 또는 재발송한다. */
    @Transactional
    public EmailVerificationSendResponse sendSignupCode(EmailVerificationSendRequest request) {
        validateRequiredAgreements(request);
        String email = normalizeEmail(request.getEmail());
        if (authMemberPort.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerification latest = emailVerificationMapper.findLatestSignupByEmailForUpdate(fieldCryptoService.emailHmac(email))
                                                           .orElse(null);
        String code = createCode();
        LocalDateTime expiresAt = now.plusMinutes(3);

        if (latest == null || shouldCreateNew(latest, now)) {
            if (latest != null) {
                emailVerificationMapper.markExpired(latest.getEmlVrfSn());
            }
            return createAndSend(email, code, now, expiresAt);
        }

        if (PENDING.equals(latest.getEmlVrfStatusCd())) {
            if (latest.getEmlVrfResendCnt() >= MAX_RESEND_COUNT) {
                emailVerificationMapper.markExpired(latest.getEmlVrfSn());
                return createAndSend(email, code, now, expiresAt);
            }
            if (latest.getEmlVrfLastSentAt().plusMinutes(1).isAfter(now)) {
                throw new CustomException(ErrorCode.EMAIL_RESEND_TOO_SOON);
            }

            emailVerificationMapper.resend(latest.getEmlVrfSn(), passwordEncoder.encode(code), expiresAt, now);
            emailSender.sendVerificationCode(email, code);
            return responseOf(latest.getEmlVrfSn(), expiresAt, now.plusMinutes(1));
        }

        emailVerificationMapper.markExpired(latest.getEmlVrfSn());
        return createAndSend(email, code, now, expiresAt);
    }

    /** 인증번호가 일치하면 해당 SIGNUP 인증 건을 VERIFIED 상태로 전환한다. */
    // 오입력·만료 예외에도 FAIL_CNT·LOCKED·EXPIRED 전이는 커밋되어야 한다.
    @Transactional(noRollbackFor = CustomException.class)
    public EmailVerificationVerifyResponse verifySignupCode(Long verificationId, EmailVerificationVerifyRequest request) {
        EmailVerification verification = findSignupForUpdate(verificationId);
        LocalDateTime now = LocalDateTime.now();
        ensureVerifiable(verification, now);

        if (verification.getEmlVrfCodeHash() == null
                || !passwordEncoder.matches(request.getCode(), verification.getEmlVrfCodeHash())) {
            emailVerificationMapper.incrementFailure(verificationId);
            if (verification.getEmlVrfFailCnt() + 1 >= MAX_FAILURE_COUNT) {
                emailVerificationMapper.lock(verificationId, now.plusMinutes(3));
            }
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        if (emailVerificationMapper.markVerified(verificationId, now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        // @ai_generated: 인증 성공 시점부터 별도 유예시간을 다시 적용한다 - 발송 시점 기준 3분 만료가
        // 그대로 남아있으면 인증 후 나머지 가입 정보 입력이 조금만 늦어져도 가입이 막히는 문제가 있었다.
        LocalDateTime graceExpiresAt = now.plusMinutes(SIGNUP_GRACE_MINUTES);
        emailVerificationMapper.extendSignupExpiry(verificationId, graceExpiresAt);
        // @ai_generated: 연장된 만료시각을 응답에 실어야 화면이 "3분"이 아니라 실제 유예시간을
        // 기준으로 판단할 수 있다 - 이전엔 Void 응답이라 화면이 이 값을 알 방법이 없었다.
        return EmailVerificationVerifyResponse.builder().expiresAt(graceExpiresAt).build();
    }

    /** 최종 가입 직전에 이메일·목적·VERIFIED 상태를 같은 트랜잭션에서 다시 검증한다. */
    @Transactional
    public void requireVerifiedSignup(Long verificationId, String email) {
        EmailVerification verification = findSignupForUpdate(verificationId);
        if (!normalizeEmail(email).equals(normalizeEmail(fieldCryptoService.decrypt(verification.getEmlVrfEmail())))) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
        ensureVerifiableForSignup(verification, LocalDateTime.now());
    }

    /** 회원과 동의 이력이 저장된 뒤에만 인증 건을 일회 사용 완료로 전환한다. */
    @Transactional
    public void markSignupUsed(Long verificationId) {
        if (emailVerificationMapper.markUsed(verificationId, LocalDateTime.now()) != 1) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
    }

    private EmailVerificationSendResponse createAndSend(String email, String code,
                                                          LocalDateTime sentAt, LocalDateTime expiresAt) {
        EmailVerification verification = EmailVerification.builder()
                .emlVrfEmail(fieldCryptoService.encrypt(email))
                .emlVrfEmailHmac(fieldCryptoService.emailHmac(email))
                .emlVrfPurposeCd(SIGNUP_PURPOSE)
                .emlVrfCodeHash(passwordEncoder.encode(code))
                .emlVrfStatusCd(PENDING)
                .emlVrfExpiresAt(expiresAt)
                .emlVrfLastSentAt(sentAt)
                .build();
        emailVerificationMapper.insertSignup(verification);
        emailSender.sendVerificationCode(email, code);
        return responseOf(verification.getEmlVrfSn(), expiresAt, sentAt.plusMinutes(1));
    }

    private boolean shouldCreateNew(EmailVerification verification, LocalDateTime now) {
        if (LOCKED.equals(verification.getEmlVrfStatusCd())) {
            if (verification.getEmlVrfRetryAt() != null && verification.getEmlVrfRetryAt().isAfter(now)) {
                throw new CustomException(ErrorCode.EMAIL_VERIFICATION_LOCKED);
            }
            return true;
        }
        if (PENDING.equals(verification.getEmlVrfStatusCd()) && !verification.getEmlVrfExpiresAt().isAfter(now)) {
            return true;
        }
        return EXPIRED.equals(verification.getEmlVrfStatusCd())
                || USED.equals(verification.getEmlVrfStatusCd())
                || VERIFIED.equals(verification.getEmlVrfStatusCd());
    }

    private void ensureVerifiable(EmailVerification verification, LocalDateTime now) {
        if (USED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
        }
        if (LOCKED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_LOCKED);
        }
        if (EXPIRED.equals(verification.getEmlVrfStatusCd()) || !verification.getEmlVrfExpiresAt().isAfter(now)) {
            emailVerificationMapper.markExpired(verification.getEmlVrfSn());
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (VERIFIED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        }
        if (!PENDING.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
    }

    private void ensureVerifiableForSignup(EmailVerification verification, LocalDateTime now) {
        if (USED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
        }
        if (EXPIRED.equals(verification.getEmlVrfStatusCd()) || !verification.getEmlVrfExpiresAt().isAfter(now)) {
            emailVerificationMapper.markExpired(verification.getEmlVrfSn());
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (LOCKED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_LOCKED);
        }
        if (!VERIFIED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
    }

    private EmailVerification findSignupForUpdate(Long verificationId) {
        return emailVerificationMapper.findSignupByIdForUpdate(verificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
    }

    private void validateRequiredAgreements(EmailVerificationSendRequest request) {
        if (!Boolean.TRUE.equals(request.getTermsAgreed()) || !Boolean.TRUE.equals(request.getPrivacyAgreed())) {
            throw new CustomException(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED);
        }
    }

    private String createCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private EmailVerificationSendResponse responseOf(Long verificationId, LocalDateTime expiresAt,
                                                      LocalDateTime resendAvailableAt) {
        return EmailVerificationSendResponse.builder()
                .verificationId(verificationId)
                .expiresAt(expiresAt)
                .resendAvailableAt(resendAvailableAt)
                .build();
    }
}
