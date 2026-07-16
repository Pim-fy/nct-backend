package nct.settlement.exception;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

public class SettlementException extends CustomException {

    private static final long serialVersionUID = 1L;

    public SettlementException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SettlementException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
