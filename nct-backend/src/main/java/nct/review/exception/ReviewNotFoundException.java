package nct.review.exception;

import nct.global.exception.ErrorCode;

/**
 * [리뷰 - 수정/삭제 대상 없음 예외]
 * - "리뷰가 존재하지 않음 / 내가 작성한 리뷰가 아님 / 이미 삭제됨" 세 경우를 하나로 묶어 처리한다.
 *   TradeNotReviewableException과 같은 이유로 세분화하지 않는다 (다른 사람 리뷰의 존재 여부를 노출하지 않기 위함).
 */
public class ReviewNotFoundException extends ReviewException {

    private static final long serialVersionUID = 1L;

    public ReviewNotFoundException(long reviewId) {
        super(ErrorCode.REVIEW_NOT_FOUND,
              "리뷰를 찾을 수 없습니다. 존재하지 않거나, 본인이 작성한 리뷰가 아니거나, 이미 삭제되었을 수 있습니다. (리뷰번호: "
                      + reviewId + ")");
    }
}
