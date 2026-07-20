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

    /**
     * 동적 메시지 생성자
     * - ErrorCode 의 고정 메시지 대신 상황별 상세 메시지를 담을 때 사용
     *   (예: 포인트 부족 시 "필요: 30,000P, 보유: 10,000P" 같은 안내)
     * - 상태 코드는 여전히 ErrorCode 가 결정하므로 응답 일관성은 유지됨
     */
    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
