package nct.global.exception;

import org.springframework.http.HttpStatus;

/**
 * [공통 API 에러 코드]
 * - HTTP 상태 코드 + 사용자 메시지를 한 곳에서 관리
 * - 도메인 확장 시 이 enum 에 항목을 추가해서 사용
 */
public enum ErrorCode {

    /*==================== 4XX CLIENT ERROR ====================*/

    // 400 Bad Request
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "필수 입력값이 누락되었습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "입력 타입이 올바르지 않습니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "토큰이 존재하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "본인의 리소스만 수정/삭제할 수 있습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다. 관리자에게 문의하세요."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다."),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // 405 / 409 / 410
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    WITHDRAWN_USER(HttpStatus.GONE, "탈퇴한 사용자입니다."),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    /*==================== 포인트/정산 도메인 (담당자6) ====================*/

    // 포인트 - 400: 요청 자체가 잘못됨 / 409: 잔액·상태와 충돌
    POINT_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "포인트 금액은 0보다 커야 합니다."),
    POINT_INSUFFICIENT(HttpStatus.CONFLICT, "사용 가능 포인트가 부족합니다."),
    POINT_DUPLICATE_HOLD(HttpStatus.CONFLICT, "이미 홀딩된 건입니다."),
    POINT_HOLD_NOT_FOUND(HttpStatus.CONFLICT, "해당 건의 유효한 홀딩이 없습니다."),

    // 정산
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산 건입니다."),
    SETTLEMENT_INVALID_STATUS(HttpStatus.CONFLICT, "현재 상태에서 허용되지 않는 정산 처리입니다."),
    SETTLEMENT_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "정산 금액은 0보다 커야 합니다."),

    // 포인트 충전 (F-PG-01)
    CHARGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 충전 주문입니다."),
    CHARGE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 충전 주문입니다."),
    CHARGE_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 승인 금액이 사전 기록과 일치하지 않습니다."),

    /*==================== 5XX SERVER ERROR ====================*/

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /** HTTP 상태 객체 */
    public HttpStatus status() {
        return status;
    }

    /** 사용자 메시지 */
    public String message() {
        return message;
    }

    /** HTTP 상태 코드 숫자 */
    public int code() {
        return status.value();
    }
}
