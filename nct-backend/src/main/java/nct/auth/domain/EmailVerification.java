package nct.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @ai_generated
/** EMAIL_VERIFICATION의 가입 인증 상태를 Mapper와 Service 사이에서 전달한다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerification {

    private Long emlVrfSn;
    private String emlVrfEmail;
    private String emlVrfPurposeCd;
    private String emlVrfCodeHash;
    private String emlVrfStatusCd;
    private LocalDateTime emlVrfExpiresAt;
    private LocalDateTime emlVrfLastSentAt;
    private int emlVrfFailCnt;
    private int emlVrfResendCnt;
    private LocalDateTime emlVrfRetryAt;
    private LocalDateTime emlVrfVerifiedAt;
    private LocalDateTime emlVrfUsedAt;
}
