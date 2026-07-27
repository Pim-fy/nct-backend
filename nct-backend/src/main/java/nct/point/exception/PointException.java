package nct.point.exception;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [포인트 - 기본 예외]
 * - 포인트 처리 규칙 위반 시 발생하는 도메인 예외의 뿌리
 * - CustomException을 상속하는 이유:
 *   1) GlobalExceptionHandler가 CustomException을 잡아 ErrorCode의 상태코드·메시지로
 *      일관된 JSON 응답을 만들어준다 (별도 핸들러 추가 불필요)
 *   2) RuntimeException 계열이므로 @Transactional 트랜잭션이 자동 롤백된다
 * - 타입을 별도로 두는 이유: 입찰·경매 등 다른 도메인이 catch로 분기하는 "서비스 계약"의 일부
 */
public class PointException extends CustomException {

    private static final long serialVersionUID = 1L;

    public PointException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
