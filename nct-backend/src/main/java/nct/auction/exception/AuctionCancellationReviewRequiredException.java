package nct.auction.exception;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * 담당자 7 · F-OPS-008: 자금 변경 전에 경매 상태가 달라져 자동 취소 대신 관리자 검토가 필요한 경우입니다.
 */
public class AuctionCancellationReviewRequiredException extends CustomException {

    private static final long serialVersionUID = 1L;

    public AuctionCancellationReviewRequiredException(String message) {
        this(ErrorCode.CONFLICT, message);
    }

    public AuctionCancellationReviewRequiredException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

