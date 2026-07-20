package nct.settlement.exception;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [정산 - 예외]
 * - 정산 상태 전이 규칙 위반·금액 오류 시 발생
 * - CustomException 상속으로 GlobalExceptionHandler가 ErrorCode 상태·메시지로 일관 응답 처리
 */
public class SettlementException extends CustomException {

    private static final long serialVersionUID = 1L;

    public SettlementException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
