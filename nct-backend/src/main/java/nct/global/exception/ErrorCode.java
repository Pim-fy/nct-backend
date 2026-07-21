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
    // F-AUC-024 지원 - 배송지 스냅샷 조회 계약(MemberService.getBuyerAddressSnapshot) 전용
    BUYER_ADDRESS_INCOMPLETE(HttpStatus.BAD_REQUEST, "회원의 배송지 정보가 등록되어 있지 않습니다."),

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
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅 메시지입니다."),

    // 405 / 409 / 410
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    REQUIRED_AGREEMENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다."),
    EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "이메일 인증 요청을 찾을 수 없습니다."),
    EMAIL_VERIFICATION_NOT_VERIFIED(HttpStatus.CONFLICT, "이메일 인증이 완료되지 않았습니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.CONFLICT, "이메일 인증번호가 만료되었습니다."),
    EMAIL_VERIFICATION_LOCKED(HttpStatus.CONFLICT, "인증번호 입력이 잠겼습니다. 잠시 후 새 인증을 요청하세요."),
    EMAIL_VERIFICATION_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 이메일 인증입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.UNAUTHORIZED, "인증번호가 일치하지 않습니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    WITHDRAWN_USER(HttpStatus.GONE, "탈퇴한 사용자입니다."),
    // @ai_generated: 작업단위5 작업 2(F-AUTH-016) - 마이페이지 연동 관리 REST API 전용
    OAUTH_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "연동된 계정을 찾을 수 없습니다."),
    OAUTH_LINK_MINIMUM_REQUIRED(HttpStatus.CONFLICT, "최소 1개의 로그인 수단은 유지해야 합니다."),
    // @ai_generated: 작업단위5(F-AUTH-004 온보딩) - 온보딩 완료 API 전용
    ONBOARDING_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "온보딩 정보를 찾을 수 없습니다. 소셜 로그인을 다시 시도해주세요."),

    // ---- 리뷰 도메인 (F-COM-007, REVIEW 테이블 고정 기술 소유 - 09_기능단위_7인_업무분장 v10) ----
    REVIEW_TRADE_NOT_REVIEWABLE(HttpStatus.CONFLICT, "리뷰를 작성할 수 없는 거래입니다."),
    REVIEW_INVALID_RATING(HttpStatus.BAD_REQUEST, "평점은 1~5점 사이여야 합니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호 재발송은 1분 후에 가능합니다."),

    /*==================== 포인트/정산 도메인 (담당자6) ====================*/

    // 포인트 - 400: 요청 자체가 잘못됨 / 409: 잔액·상태와 충돌
    POINT_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "포인트 금액은 0보다 커야 합니다."),
    POINT_INSUFFICIENT(HttpStatus.CONFLICT, "사용 가능 포인트가 부족합니다."),
    POINT_CONVERT_BLOCKED_BY_DISPUTE(HttpStatus.CONFLICT, "진행 중인 거래 문제가 있어 포인트 전환을 할 수 없습니다. 거래 문제 처리 완료 후 다시 시도해 주세요."),
    POINT_DUPLICATE_HOLD(HttpStatus.CONFLICT, "이미 홀딩된 건입니다."),
    POINT_HOLD_NOT_FOUND(HttpStatus.CONFLICT, "해당 건의 유효한 홀딩이 없습니다."),

    // 포인트 - 보관금(에스크로) 계약 (F-SVC-013/015, 분쟁 환불 — 2026-07-20)
    POINT_DUPLICATE_ESCROW(HttpStatus.CONFLICT, "이미 보관금이 결제된 건입니다."),
    POINT_ESCROW_NOT_FOUND(HttpStatus.CONFLICT, "해당 건의 유효한 보관금이 없습니다."),
    POINT_ESCROW_ALREADY_SETTLED(HttpStatus.CONFLICT, "이미 정산 지급이 끝난 보관금입니다."),
    POINT_SETTLE_BLOCKED_BY_DISPUTE(HttpStatus.CONFLICT, "해당 거래에 진행 중인 거래 문제가 있어 정산 전환을 할 수 없습니다. 거래 문제 처리 완료 후 다시 시도해 주세요."),

    // 정산
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산 건입니다."),
    SETTLEMENT_INVALID_STATUS(HttpStatus.CONFLICT, "현재 상태에서 허용되지 않는 정산 처리입니다."),
    SETTLEMENT_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "정산 금액은 0보다 커야 합니다."),

    // 포인트 충전 (F-PG-01)
    CHARGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 충전 주문입니다."),
    CHARGE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 충전 주문입니다."),
    CHARGE_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 승인 금액이 사전 기록과 일치하지 않습니다."),
    CHARGE_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "충전 금액이 허용 범위를 벗어났습니다."),
    CHARGE_ORDER_EXPIRED(HttpStatus.CONFLICT, "시간이 만료된 충전 주문입니다."),
    CHARGE_INTERNAL_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "충전 처리 중 오류가 발생하여 결제를 취소 처리했습니다."),

    // 시스템 설정 읽기 계약 (F-OPS-024 SYSTEM_SETTING, 타 도메인 소비용 — 값 이상 시 임의 기본값 대신 실패)
    SYSTEM_SETTING_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "시스템 설정 값을 확인할 수 없어 요청을 처리할 수 없습니다."),

    // 포인트 환전 (F-PAY-012)
    EXCHANGE_ACCOUNT_NOT_REGISTERED(HttpStatus.BAD_REQUEST, "환전 계좌가 등록되어 있지 않습니다. 마이페이지에서 계좌를 먼저 등록해 주세요."),
    EXCHANGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 환전 신청입니다."),
    EXCHANGE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 환전 신청입니다."),

    /*==================== 상품/경매 도메인 (담당자2) ====================*/

    PRODUCT_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 상품만 처리할 수 있습니다."),
    PRODUCT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 상품입니다."),
    PRODUCT_CANCEL_INVALID_STATUS(HttpStatus.CONFLICT, "취소 요청이 불가능한 경매 상태입니다."),
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경매입니다."),

    /*==================== 파일 도메인 (담당자6, F-AUC-002 이미지 연계) ====================*/

    FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있습니다."),
    // 허용 확장자는 서비스 구분별로 다름(FileStorageService.SERVICE_EXTENSIONS) — 특정 목록을 박지 않는 중립 문구 (2026-07-20)
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기는 10MB를 초과할 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),
    FILE_INVALID_SERVICE(HttpStatus.BAD_REQUEST, "허용되지 않은 첨부 서비스 구분입니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인이 업로드한 파일만 삭제/변경할 수 있습니다."),
    FILE_IN_USE(HttpStatus.CONFLICT, "사용 중인 파일은 삭제할 수 없습니다."),
    // 배송 인증사진(F-AUC-009) — 거래 당사자만 열람 (2026-07-20)
    FILE_TRADE_PARTY_ONLY(HttpStatus.FORBIDDEN, "거래 당사자만 열람할 수 있습니다."),

    /*==================== 5XX SERVER ERROR ====================*/

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다."),
    EMAIL_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이메일 발송 서비스를 사용할 수 없습니다."),
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
