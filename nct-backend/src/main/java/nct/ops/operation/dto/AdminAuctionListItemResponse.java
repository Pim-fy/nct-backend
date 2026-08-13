package nct.ops.operation.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · F-OPS-003: 관리자 목록에서 필요한 경매·거래·취소요청 요약입니다. */
@Getter
@Setter
public class AdminAuctionListItemResponse {
    private Long auctionId;
    private Long productId;
    private String productName;
    private Long sellerUserSn;
    private String sellerName;
    private AdminMemberIdentityResponse sellerMember;
    private String productStatusCode;
    private String productStatusName;
    private String productUseYn;
    private String auctionStatusCode;
    private String auctionStatusName;
    private Integer bidCount;
    private Long tradeId;
    private String tradeStatusCode;
    private String tradeStatusName;
    private LocalDateTime registeredAt;
    private Long cancelRequestId;
    private String cancelReason;
    private LocalDateTime cancelRequestedAt;
    private String previousAuctionStatusCode;
    private String cancelApprovedYn;
    private Long cancelProcessorUserSn;
    private AdminMemberIdentityResponse cancelProcessorMember;
    private String cancelProcessReason;
    private LocalDateTime cancelProcessedAt;
}
