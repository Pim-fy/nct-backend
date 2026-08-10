package nct.settlement.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 관리자 정산 보류·해제 이력과 요청 멱등키를 보존하는 내부 모델입니다. */
@Getter
@Setter
@NoArgsConstructor
public class SettlementAdminAction {

    private Long actionSn;
    private Long settlementSn;
    private String actionType;
    private String previousStatusCode;
    private String nextStatusCode;
    private String reason;
    private String requestId;
    private Long processorUserSn;
    private LocalDateTime processedAt;
}
