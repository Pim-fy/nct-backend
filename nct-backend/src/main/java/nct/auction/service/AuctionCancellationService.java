package nct.auction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.AuctionCancelRequestRecord;
import nct.auction.dto.AuctionCancellationTarget;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.service.PointService;

@Service
@RequiredArgsConstructor
public class AuctionCancellationService {

    private final AuctionCancelRequestMapper cancelRequestMapper;
    private final PointService pointService;

    @Transactional
    public void requestCancellation(Long aucSn, Long requesterUsrSn, String reason) {
        String normalizedReason = requireText(reason, ErrorCode.INVALID_INPUT_VALUE);
        AuctionCancellationTarget target = findAuctionTarget(aucSn);

        if (!requesterUsrSn.equals(target.getSellerId())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!AuctionStatusCode.ACTIVE.equals(target.getAuctionStatusCode())) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }
        if (cancelRequestMapper.countPendingByAuction(aucSn) > 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_DUPLICATED);
        }

        int inserted = cancelRequestMapper.insertCancelRequest(
                aucSn,
                requesterUsrSn,
                normalizedReason,
                requesterUsrSn.toString());
        if (inserted == 0) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        int updated = cancelRequestMapper.updateAuctionStatus(
                aucSn,
                AuctionStatusCode.ACTIVE,
                AuctionStatusCode.CANCEL_REQUESTED,
                requesterUsrSn.toString());
        if (updated == 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }
    }

    @Transactional
    public void approveCancellation(Long cancelRequestSn, Long adminUsrSn, String processReason) {
        AuctionCancelRequestRecord request = findPendingRequest(cancelRequestSn);
        AuctionCancellationTarget target = findAuctionTargetByCancelRequest(cancelRequestSn);

        if (!AuctionStatusCode.CANCEL_REQUESTED.equals(target.getAuctionStatusCode())) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }

        cancelHighestBid(target, adminUsrSn);

        int requestUpdated = cancelRequestMapper.approveCancelRequest(
                cancelRequestSn,
                adminUsrSn,
                normalizeNullable(processReason),
                adminUsrSn.toString());
        if (requestUpdated == 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PROCESSED);
        }

        int auctionUpdated = cancelRequestMapper.updateAuctionStatus(
                request.getAuctionId(),
                AuctionStatusCode.CANCEL_REQUESTED,
                AuctionStatusCode.CANCELED,
                adminUsrSn.toString());
        if (auctionUpdated == 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }
    }

    @Transactional
    public void rejectCancellation(Long cancelRequestSn, Long adminUsrSn, String rejectReason) {
        String normalizedReason = requireText(rejectReason, ErrorCode.INVALID_INPUT_VALUE);
        AuctionCancelRequestRecord request = findPendingRequest(cancelRequestSn);
        AuctionCancellationTarget target = findAuctionTargetByCancelRequest(cancelRequestSn);

        if (!AuctionStatusCode.CANCEL_REQUESTED.equals(target.getAuctionStatusCode())) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }

        int requestUpdated = cancelRequestMapper.rejectCancelRequest(
                cancelRequestSn,
                adminUsrSn,
                normalizedReason,
                adminUsrSn.toString());
        if (requestUpdated == 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PROCESSED);
        }

        int auctionUpdated = cancelRequestMapper.updateAuctionStatus(
                request.getAuctionId(),
                AuctionStatusCode.CANCEL_REQUESTED,
                AuctionStatusCode.ACTIVE,
                adminUsrSn.toString());
        if (auctionUpdated == 0) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_INVALID_STATUS);
        }
    }

    private AuctionCancellationTarget findAuctionTarget(Long aucSn) {
        AuctionCancellationTarget target = cancelRequestMapper.findAuctionCancellationTargetForUpdate(aucSn);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return target;
    }

    private AuctionCancellationTarget findAuctionTargetByCancelRequest(Long cancelRequestSn) {
        AuctionCancellationTarget target =
                cancelRequestMapper.findAuctionCancellationTargetByCancelRequestForUpdate(cancelRequestSn);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_NOT_FOUND);
        }
        return target;
    }

    private AuctionCancelRequestRecord findPendingRequest(Long cancelRequestSn) {
        AuctionCancelRequestRecord request = cancelRequestMapper.findCancelRequestForUpdate(cancelRequestSn);
        if (request == null) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_NOT_FOUND);
        }
        if (request.getApprovalYn() != null) {
            throw new CustomException(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PROCESSED);
        }
        return request;
    }

    private void cancelHighestBid(AuctionCancellationTarget target, Long adminUsrSn) {
        if (target.getHighestBidId() == null) {
            return;
        }

        int bidUpdated = cancelRequestMapper.updateBidStatus(
                target.getHighestBidId(),
                BidStatusCode.HIGHEST,
                BidStatusCode.EXCEPTION_CANCELED,
                adminUsrSn.toString());
        if (bidUpdated == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "최고입찰 상태가 변경되었습니다.");
        }

        pointService.releaseHold(
                target.getHighestBidderId(),
                RefType.BID,
                target.getHighestBidId(),
                "경매 취소 승인에 따른 입찰 홀딩 반환");
    }

    private String requireText(String value, ErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new CustomException(errorCode);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
