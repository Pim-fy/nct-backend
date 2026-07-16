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
