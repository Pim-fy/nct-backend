package nct.auction.service;

import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionCancelRequestCreateCommand;
import nct.auction.dto.AuctionCancelRequestResponse;
import nct.auction.dto.AuctionCancellationTarget;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.auction.mapper.AuctionMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;

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
}
