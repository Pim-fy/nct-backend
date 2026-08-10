package nct.trade.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 직거래 일정 제안 이력과 거래 당사자 검증에 사용하는 조회 모델이다. */
@Data
public class TradeOfflineScheduleProposal {

    private Long proposalId;
    private Long tradeId;
    private String proposalType;
    private String proposalStatus;
    private Long proposerUserId;
    private Long responderUserId;
    private LocalDateTime meetingDateTime;
    private String meetingPlace;
    private String meetingAddress;
    private String reason;
    private LocalDateTime respondedAt;
}
