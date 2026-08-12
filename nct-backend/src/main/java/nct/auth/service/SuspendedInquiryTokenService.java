package nct.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auth.domain.EmailVerification;
import nct.auth.mapper.EmailVerificationMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.utils.TokenHashUtil;

// @ai_generated
/**
 * F-AUTH-017/POL-AUTH-016: 정지 계정 비로그인 문의 접수용 단발성 토큰.
 * 로그인 시도 시 비밀번호가 이미 검증된 직후(=본인확인 완료)에만 발급하므로, 탈퇴처럼 별도
 * 이메일 왕복을 거치지 않고 토큰 원문을 응답에 직접 반환한다(이메일 발송 없음). 검증 성공
 * 즉시 USED로 전환해 토큰 1개당 문의 1건만 허용한다(재시도하려면 다시 로그인 시도해서
 * 새 토큰을 받아야 한다).
 */
@Service
@RequiredArgsConstructor
public class SuspendedInquiryTokenService {

    private static final String PENDING = "EMVC0003";
    private static final String VERIFIED = "EMVC0004";
    private static final String USED = "EMVC0005";
    private static final String EXPIRED = "EMVC0006";
    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_MINUTES = 15;

    private final EmailVerificationMapper emailVerificationMapper;
    private final AuthMemberPort authMemberPort;
    private final FieldCryptoService fieldCryptoService;
    private final TokenHashUtil tokenHashUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 로그인 시도 중 정지 판정 직후(=비밀번호 검증 통과 직후) 호출된다. 이미 진행 중인 트랜잭션에 참여한다. */
    public String issueToken(String email) {
        String token = createToken();
        LocalDateTime now = LocalDateTime.now();
        EmailVerification verification = EmailVerification.builder()
                .emlVrfEmail(fieldCryptoService.encrypt(email))
                .emlVrfEmailHmac(fieldCryptoService.emailHmac(email))
                .emlVrfCodeHash(tokenHashUtil.hash(token))
                .emlVrfStatusCd(PENDING)
                .emlVrfExpiresAt(now.plusMinutes(EXPIRY_MINUTES))
                .emlVrfLastSentAt(now)
                .build();
        emailVerificationMapper.insertSuspendedInquiry(verification);
        return token;
    }

    /** 문의 접수 엔드포인트에서 호출된다. 유효하면 즉시 USED로 소진하고 usrSn을 반환한다. */
    @Transactional
    public Long consumeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);
        }
        String tokenHash = tokenHashUtil.hash(rawToken);
        EmailVerification verification = emailVerificationMapper
                .findSuspendedInquiryByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        ensureConfirmable(verification, now);

        if (emailVerificationMapper.markVerified(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (emailVerificationMapper.markUsed(verification.getEmlVrfSn(), now) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        String plainEmail = fieldCryptoService.decrypt(verification.getEmlVrfEmail());
        AuthMember member = authMemberPort.findByEmail(plainEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return member.getId();
    }

    private void ensureConfirmable(EmailVerification verification, LocalDateTime now) {
        if (USED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
        }
        if (EXPIRED.equals(verification.getEmlVrfStatusCd()) || !verification.getEmlVrfExpiresAt().isAfter(now)) {
            emailVerificationMapper.markExpired(verification.getEmlVrfSn());
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (!PENDING.equals(verification.getEmlVrfStatusCd()) && !VERIFIED.equals(verification.getEmlVrfStatusCd())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
    }

    private String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
