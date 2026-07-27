package nct.review.exception;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/** [리뷰 - 기본 예외] CustomException 을 상속해 GlobalExceptionHandler가 자동으로 ApiResponse 로 변환한다. */
public class ReviewException extends CustomException {

    private static final long serialVersionUID = 1L;

    public ReviewException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
