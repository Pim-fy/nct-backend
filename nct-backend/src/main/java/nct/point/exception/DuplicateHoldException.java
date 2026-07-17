package nct.point.exception;

import nct.common.domain.RefType;
import nct.global.exception.ErrorCode;

/**
 * [포인트 - 중복 홀딩 예외] (ML-PAY-002: 중복 홀딩 방지)
 * - 같은 참조 건(예: 같은 입찰)에 유효한 홀딩이 이미 있는데 또 홀딩하려 할 때 발생
 * - 네트워크 재전송·더블클릭으로 인한 이중 차감을 막는 안전장치
 */
public class DuplicateHoldException extends PointException {

    private static final long serialVersionUID = 1L;

    public DuplicateHoldException(RefType refType, long refSn) {
        super(ErrorCode.POINT_DUPLICATE_HOLD,
              "이미 홀딩이 존재합니다. 참조: " + refType + "-" + refSn);
    }
}
