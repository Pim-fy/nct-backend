package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/** 구매자·판매자 거래 상세 화면이 함께 사용하는 조회 전용 응답이다. */
@Data
public class TradeDetailResponse {

    private Long auctionId;
    // @ai_generated (담당자1 황희준, 2026-08-07): Mapper는 AUCTION을 직접 JOIN하지 않고 이 값만
    // 채운다. TradeService가 AuctionService 계약으로 auctionId를 채운 뒤에는 쓰지 않는 내부 값이라
    // API 응답에는 노출하지 않는다.
    @JsonIgnore
    private Long productId;
    private Long tradeId;
    // @ai_generated: 신규 경로 계약명. userRole은 기존 화면 호환을 위해 함께 유지한다.
    private String viewerRole;
    private String userRole;
    private String productName;
    private String category;
    private String productImageUrl;
    private Long counterpartUserId;
    private String counterpartNickname;
    private String counterpartProfileImageUrl;
    private LocalDateTime counterpartJoinedAt;
    private int counterpartCompletedTradeCount;
    private BigDecimal tradeAmount;
    private String tradeStatus;
    private String tradeMethod;
    /** 직거래 채팅방이 아직 없거나 활성·종료되었는지 상세 화면에 제공한다. */
    private String chatRoomStatus;
    // 확인 대기 상태에서 첫 완료 확인을 누른 당사자 역할(BUYER/SELLER)을 화면에 제공한다.
    private String completionRequestedBy;
    private LocalDateTime createdAt;
    // @ai_generated: 리뷰 화면이 거래 생성일을 완료일로 오인하지 않도록 상태이력의 완료 시점을 제공한다.
    private LocalDateTime completedAt;
    private LocalDateTime autoCompleteAt;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    // @ai_generated: Mapper 경계에서는 배송 상세주소 암호문을 별도 보관하고 서비스가 합쳐서 응답한다.
    private String deliveryDetailAddress;
    private String deliveryMessage;
    private Long deliveryId;
    // @ai_generated: TRADE_DELIVERY 행은 낙찰 시점에 배송지 스냅샷과 함께 먼저 생성되므로
    // TRD_DLVR_REG_DT(등록일시)는 발송 인증 시각이 아니다. 발송 인증 제출이 이 행을 갱신하는
    // 유일한 경로라 TRD_DLVR_UPDT_DT(갱신일시)를 발송 인증 등록 시각으로 사용한다.
    private LocalDateTime deliveryProofRegisteredAt;
    private List<TradeDeliveryProofFile> deliveryProofFiles;
    private LocalDateTime meetingDateTime;
    private String meetingPlace;
    private String meetingAddress;
    /** 현재 확정 일정과 분리해 반환하는 대기 중 일정 제안 정보다. */
    private Long pendingScheduleProposalId;
    private String pendingScheduleProposalType;
    private String pendingScheduleProposalStatus;
    private String pendingScheduleProposalProposerRole;
    private LocalDateTime pendingMeetingDateTime;
    private String pendingMeetingPlace;
    private String pendingMeetingAddress;
    private boolean canRespondToScheduleProposal;
    private boolean canWithdrawScheduleProposal;
}
