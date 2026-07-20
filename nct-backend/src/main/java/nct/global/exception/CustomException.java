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

    /** 동적 메시지 버전 - 사용 예: throw new CustomException(ErrorCode.POINT_INSUFFICIENT, "필요: 3만P, 보유: 1만P") */
    public CustomException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
