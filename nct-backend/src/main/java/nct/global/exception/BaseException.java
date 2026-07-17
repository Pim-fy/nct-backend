package nct.global.exception;

import lombok.Getter;

/**
 * [예외 계층의 최상위]
 * - RuntimeException 기반 -> @Transactional 자동 롤백 대상
 * - ErrorCode 를 보유해 핸들러에서 상태코드/메시지를 일관 처리
 */
@Getter
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** 동적 메시지 버전 - ErrorCode의 고정 메시지 대신 상황에 맞는 구체적 메시지를 담고 싶을 때 사용 */
    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
