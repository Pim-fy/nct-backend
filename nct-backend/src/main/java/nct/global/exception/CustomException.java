package nct.global.exception;

/**
 * [비즈니스 예외]
 * - 사용 예: throw new CustomException(ErrorCode.USER_NOT_FOUND);
 */
public class CustomException extends BaseException {

    private static final long serialVersionUID = 1L;

    public CustomException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 동적 메시지 버전 - 사용 예: throw new CustomException(ErrorCode.REVIEW_TRADE_NOT_REVIEWABLE, "거래번호: 123") */
    public CustomException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
