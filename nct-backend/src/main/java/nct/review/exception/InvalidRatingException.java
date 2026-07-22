package nct.review.exception;

import nct.global.exception.ErrorCode;

/** [리뷰 - 평점 범위 오류] DB에도 CHK_REVIEW_SCORE(1~5) 제약이 있지만, 서버가 먼저 걸러 명확한 메시지를 준다 */
public class InvalidRatingException extends ReviewException {

    private static final long serialVersionUID = 1L;

    public InvalidRatingException(int rating) {
        super(ErrorCode.REVIEW_INVALID_RATING, "평점은 1~5점 사이여야 합니다. 입력값: " + rating);
    }
}
