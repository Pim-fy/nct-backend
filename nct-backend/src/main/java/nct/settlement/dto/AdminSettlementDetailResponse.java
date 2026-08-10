package nct.settlement.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** F-OPS-009 관리자 정산 상세와 최근 처리 정보입니다. */
@Getter
@Builder
public class AdminSettlementDetailResponse {

    private final Long settlementId;
    private final Long tradeId;
    private final Long userId;
    private final String userName;
    private final long amount;
    private final String statusCode;
    private final String statusName;
    private final LocalDateTime registeredAt;
    private final LocalDateTime updatedAt;
    private final String lastActionType;
    private final String processReason;
    private final Long processorUserId;
    private final String processorName;
    private final LocalDateTime processedAt;

    public static AdminSettlementDetailResponse from(AdminSettlementRecord record) {
        return AdminSettlementDetailResponse.builder()
                .settlementId(record.getSettlementId())
                .tradeId(record.getTradeId())
                .userId(record.getUserId())
                .userName(record.getUserName())
                .amount(record.getAmount())
                .statusCode(record.getStatusCode())
                .statusName(record.getStatusName())
                .registeredAt(record.getRegisteredAt())
                .updatedAt(record.getUpdatedAt())
                .lastActionType(record.getLastActionType())
                .processReason(record.getProcessReason())
                .processorUserId(record.getProcessorUserId())
                .processorName(record.getProcessorName())
                .processedAt(record.getProcessedAt())
                .build();
    }
}
