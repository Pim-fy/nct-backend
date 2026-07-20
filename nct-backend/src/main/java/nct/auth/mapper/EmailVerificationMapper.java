package nct.auth.mapper;

import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auth.domain.EmailVerification;

// @ai_generated
/** 가입 인증 상태를 행 잠금으로 갱신하는 EMAIL_VERIFICATION 전용 Mapper다. */
@Mapper
public interface EmailVerificationMapper {

    Optional<EmailVerification> findLatestSignupByEmailForUpdate(@Param("email") String email);

    Optional<EmailVerification> findSignupByIdForUpdate(@Param("verificationId") Long verificationId);

    void insertSignup(EmailVerification verification);

    // @ai_generated: F-AUTH-007 - PASSWORD_RESET(EMVC0002) 전용. SIGNUP 메서드와 분리해 재사용한다.
    Optional<EmailVerification> findLatestPasswordResetByEmailForUpdate(@Param("email") String email);

    void insertPasswordReset(EmailVerification verification);

    // @ai_generated: 링크 방식은 PK 대신 토큰 해시로 조회한다(URL에 verificationId를 노출하지 않음).
    Optional<EmailVerification> findPasswordResetByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int resend(@Param("verificationId") Long verificationId,
               @Param("codeHash") String codeHash,
               @Param("expiresAt") LocalDateTime expiresAt,
               @Param("sentAt") LocalDateTime sentAt);

    int markExpired(@Param("verificationId") Long verificationId);

    int markVerified(@Param("verificationId") Long verificationId,
                     @Param("verifiedAt") LocalDateTime verifiedAt);

    int incrementFailure(@Param("verificationId") Long verificationId);

    int lock(@Param("verificationId") Long verificationId,
             @Param("retryAt") LocalDateTime retryAt);

    int markUsed(@Param("verificationId") Long verificationId,
                 @Param("usedAt") LocalDateTime usedAt);
}
