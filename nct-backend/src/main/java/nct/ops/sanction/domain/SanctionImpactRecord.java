package nct.ops.sanction.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 7 · 신고 처리 제재: 정지 때문에 중지·취소된 도메인 항목과 복구용 이전 상태를 보존합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SanctionImpactRecord {

    private Long impactSn;
    private Long sanctionSn;
    private String referenceTypeCode;
    private Long referenceSn;
    private String roleCode;
    private String actionCode;
    private String statusCode;
    private String previousStatusCode;
    private LocalDateTime previousStartAt;
    private LocalDateTime previousDeadlineAt;
    private Long remainingStartSeconds;
    private Long remainingSeconds;
    private boolean settlementHeld;
    private String result;
    private String actorId;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
