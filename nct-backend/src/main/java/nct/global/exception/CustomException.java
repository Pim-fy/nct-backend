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
}
