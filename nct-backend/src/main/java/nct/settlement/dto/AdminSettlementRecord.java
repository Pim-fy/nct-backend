package nct.settlement.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** F-OPS-009 관리자 정산 목록·상세 조회용 Mapper 결과입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminSettlementRecord {

    private Long settlementId;
    private Long tradeId;
    private Long userId;
    private String userName;
    private long amount;
    private String statusCode;
    private String statusName;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private String lastActionType;
    private String processReason;
    private Long processorUserId;
    private String processorName;
    private LocalDateTime processedAt;
}
