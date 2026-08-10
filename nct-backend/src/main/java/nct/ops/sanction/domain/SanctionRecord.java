package nct.ops.sanction.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** SANCTION 명령 멱등성·활성 제재·이력 조회에 사용하는 담당자5 내부 모델입니다. */
@Getter
@Setter
@NoArgsConstructor
public class SanctionRecord {

    private Long sanctionSn;
    private Long userSn;
    private Long processorUserSn;
    private String sanctionTypeCode;
    private String sanctionTypeName;
    private String reason;
    private String restrictRequestId;
    private String releaseRequestId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
