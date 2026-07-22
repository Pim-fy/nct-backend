package nct.auction.service;

import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionCancelRequestCreateCommand;
import nct.auction.dto.AuctionCancelRequestProcessTarget;
import nct.auction.dto.AuctionCancelRequestResponse;
import nct.auction.dto.AuctionCancellationTarget;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.auction.mapper.AuctionMapper;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.service.TradeService;

@Service
@RequiredArgsConstructor
public class AuctionCancellationService {

    private static final String AUCTION_STATUS_GROUP_CODE = "AUCG01";
    private static final int MAX_REASON_LENGTH = 1000;
    private static final Set<String> REQUESTABLE_STATUSES = Set.of(
            AuctionStatusCode.ACTIVE,
            AuctionStatusCode.ENDED);

    private final AuctionMapper auctionMapper;
    private final AuctionCancelRequestMapper cancelRequestMapper;
    private final ReferenceDataService referenceDataService;
    private final TradeService tradeService;
    private final PointService pointService;

    @Transactional
    public AuctionCancelRequestResponse requestCancellation(
            Long aucSn,
            Long requesterUsrSn,
            String reason) {
        validateRequestValues(aucSn, requesterUsrSn, reason);
        String normalizedReason = reason.trim();

        AuctionCancellationTarget target = auctionMapper.findAuctionCancellationTargetForUpdate(aucSn);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        if (!requesterUsrSn.equals(target.getSellerUsrSn())) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_OWNER);
        }
        if (cancelRequestMapper.existsPendingByAuctionId(aucSn)) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PENDING);
        }
        if (!REQUESTABLE_STATUSES.contains(target.getAucStatusCd())) {
            throw new CustomException(ErrorCode.PRODUCT_CANCEL_INVALID_STATUS);
        }

        referenceDataService.requireActiveCode(
                AUCTION_STATUS_GROUP_CODE,
                AuctionStatusCode.CANCEL_REQUESTED);

        AuctionCancelRequestCreateCommand command = new AuctionCancelRequestCreateCommand(
                aucSn,
                requesterUsrSn,
                normalizedReason,
                target.getAucStatusCd(),
                requesterUsrSn.toString());

        insertCancelRequest(command);
        int updated = auctionMapper.updateAuctionStatusForCancellation(
                aucSn,
                target.getAucStatusCd(),
                AuctionStatusCode.CANCEL_REQUESTED,
                requesterUsrSn.toString());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 이미 변경되었습니다.");
        }

        return AuctionCancelRequestResponse.builder()
                .aucCnlReqSn(command.getAucCnlReqSn())
                .aucSn(aucSn)
                .prevAucStatusCd(target.getAucStatusCd())
                .aucStatusCd(AuctionStatusCode.CANCEL_REQUESTED)
                .build();
    }

    /** 관리자 승인: BID·포인트·거래·경매·요청 이력을 하나의 트랜잭션에서 취소한다. */
    @Transactional
    public void approveCancellation(
            Long cancelRequestSn,
            Long adminUsrSn,
            String processReason) {
        String reason = validateProcessValues(cancelRequestSn, adminUsrSn, processReason);
        AuctionCancelRequestProcessTarget request = requirePendingRequest(cancelRequestSn);
        AuctionBidTarget auction = requireCancelRequestedAuction(request);

        referenceDataService.requireActiveCode(
                AUCTION_STATUS_GROUP_CODE,
                AuctionStatusCode.CANCELED);

        if (AuctionStatusCode.ACTIVE.equals(request.getPreviousAuctionStatusCode())) {
            cancelActiveAuctionBid(auction, adminUsrSn, reason);
        } else if (AuctionStatusCode.ENDED.equals(request.getPreviousAuctionStatusCode())) {
            cancelEndedAuctionTrade(auction, cancelRequestSn, adminUsrSn, reason);
        } else {
            throw new CustomException(ErrorCode.CONFLICT, "취소 요청의 이전 경매 상태가 올바르지 않습니다.");
        }

        updateAuctionStatus(
                request.getAuctionId(),
                AuctionStatusCode.CANCEL_REQUESTED,
                AuctionStatusCode.CANCELED,
                adminUsrSn);
        processRequest(cancelRequestSn, "Y", adminUsrSn, reason);
    }

    /** 관리자 반려: 포인트와 거래는 건드리지 않고 경매만 요청 전 상태로 복귀시킨다. */
    @Transactional
    public void rejectCancellation(
            Long cancelRequestSn,
            Long adminUsrSn,
            String rejectReason) {
        String reason = validateProcessValues(cancelRequestSn, adminUsrSn, rejectReason);
        AuctionCancelRequestProcessTarget request = requirePendingRequest(cancelRequestSn);
        requireCancelRequestedAuction(request);

        referenceDataService.requireActiveCode(
                AUCTION_STATUS_GROUP_CODE,
                request.getPreviousAuctionStatusCode());
        updateAuctionStatus(
                request.getAuctionId(),
                AuctionStatusCode.CANCEL_REQUESTED,
                request.getPreviousAuctionStatusCode(),
                adminUsrSn);
        processRequest(cancelRequestSn, "N", adminUsrSn, reason);
    }

    private AuctionCancelRequestProcessTarget requirePendingRequest(Long cancelRequestSn) {
        AuctionCancelRequestProcessTarget request =
                cancelRequestMapper.findProcessTargetForUpdate(cancelRequestSn);
        if (request == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않는 경매 취소 요청입니다.");
        }
        if (request.getApprovalYn() != null) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED, "이미 처리된 경매 취소 요청입니다.");
        }
        return request;
    }

    private AuctionBidTarget requireCancelRequestedAuction(
            AuctionCancelRequestProcessTarget request) {
        AuctionBidTarget auction = auctionMapper.findAuctionBidTargetForUpdate(request.getAuctionId());
        if (auction == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        if (!AuctionStatusCode.CANCEL_REQUESTED.equals(auction.getAuctionStatusCode())) {
            throw new CustomException(ErrorCode.CONFLICT, "경매가 취소 요청 상태가 아닙니다.");
        }
        return auction;
    }

    private void cancelActiveAuctionBid(
            AuctionBidTarget auction,
            Long adminUsrSn,
            String reason) {
        if (auction.getCurrentHighestBidId() == null) {
            return;
        }
        if (auction.getCurrentHighestBidderId() == null) {
            throw new CustomException(ErrorCode.CONFLICT, "최고입찰자 정보를 확인할 수 없습니다.");
        }

        exceptionCancelHighestBid(auction, adminUsrSn);
        pointService.releaseHold(
                auction.getCurrentHighestBidderId(),
                RefType.BID,
                auction.getCurrentHighestBidId(),
                "경매 취소 승인 홀딩 반환: " + reason);
    }

    private void cancelEndedAuctionTrade(
            AuctionBidTarget auction,
            Long cancelRequestSn,
            Long adminUsrSn,
            String reason) {
        AuctionTradeEscrowInfo escrow = tradeService
                .findAuctionTradeEscrowInfoByProductId(auction.getProductId())
                .orElseThrow(() -> new CustomException(
                        ErrorCode.CONFLICT,
                        "경매 거래 정보를 확인할 수 없어 취소를 승인할 수 없습니다."));

        if (escrow.getBidSn() == null
                || auction.getCurrentHighestBidId() == null
                || auction.getCurrentHighestBidderId() == null
                || !escrow.getBidSn().equals(auction.getCurrentHighestBidId())
                || escrow.getBuyerUsrSn() != auction.getCurrentHighestBidderId()) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "낙찰 입찰과 거래 보관금 정보가 일치하지 않습니다.");
        }

        tradeService.decide(new SellerCancellationDecisionCommand(
                escrow.getTradeSn(),
                SellerCancellationDecision.APPROVED,
                reason,
                adminUsrSn.toString(),
                "AUC-CANCEL-" + cancelRequestSn));
        exceptionCancelHighestBid(auction, adminUsrSn);
        pointService.refundEscrow(
                escrow.getBuyerUsrSn(),
                escrow.getTradeSn(),
                RefType.BID,
                escrow.getBidSn(),
                "경매 취소 승인 보관금 환불: " + reason);
    }

    private void exceptionCancelHighestBid(AuctionBidTarget auction, Long adminUsrSn) {
        int updated = auctionMapper.exceptionCancelHighestBid(
                auction.getAuctionId(),
                auction.getCurrentHighestBidId(),
                adminUsrSn.toString());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "낙찰 입찰 상태가 이미 변경되었습니다.");
        }
    }

    private void updateAuctionStatus(
            Long auctionId,
            String expectedStatus,
            String newStatus,
            Long adminUsrSn) {
        int updated = auctionMapper.updateAuctionStatusForCancellation(
                auctionId,
                expectedStatus,
                newStatus,
                adminUsrSn.toString());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 이미 변경되었습니다.");
        }
    }

    private void processRequest(
            Long cancelRequestSn,
            String approvalYn,
            Long adminUsrSn,
            String reason) {
        int updated = cancelRequestMapper.processPendingRequest(
                cancelRequestSn,
                approvalYn,
                adminUsrSn,
                reason,
                adminUsrSn.toString());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 취소 요청이 이미 처리되었습니다.");
        }
    }

    private void insertCancelRequest(AuctionCancelRequestCreateCommand command) {
        try {
            int inserted = cancelRequestMapper.insertCancelRequest(command);
            if (inserted != 1 || command.getAucCnlReqSn() == null) {
                throw new CustomException(ErrorCode.CONFLICT, "경매 취소 요청 등록에 실패했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PENDING);
        }
    }

    private void validateRequestValues(Long aucSn, Long requesterUsrSn, String reason) {
        if (aucSn == null || requesterUsrSn == null || reason == null || reason.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (reason.trim().length() > MAX_REASON_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String validateProcessValues(
            Long cancelRequestSn,
            Long adminUsrSn,
            String reason) {
        if (cancelRequestSn == null
                || cancelRequestSn <= 0
                || adminUsrSn == null
                || adminUsrSn <= 0
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > MAX_REASON_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reason.trim();
    }
}
