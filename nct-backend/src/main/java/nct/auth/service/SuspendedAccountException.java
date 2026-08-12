package nct.auth.service;

import lombok.Getter;

// @ai_generated
/**
 * F-AUTH-017/POL-AUTH-016: 로그인 시도가 정지 계정으로 판정될 때만 던진다(비밀번호는 이미
 * 검증된 뒤라는 전제). 문의 접수용 단발성 토큰을 함께 실어 컨트롤러가 응답에 포함시킨다.
 * 공용 CustomException(ErrorCode)이 아닌 별도 타입인 이유: 토큰이라는 구조화 데이터를
 * ErrorCode enum 계약에 억지로 끼워 넣지 않기 위함 - AuthController가 이 타입만 별도로 잡아
 * 응답을 구성하고, 공용 GlobalExceptionHandler는 건드리지 않는다.
 */
@Getter
public class SuspendedAccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String inquiryToken;

    public SuspendedAccountException(String inquiryToken) {
        super("정지된 계정입니다.");
        this.inquiryToken = inquiryToken;
    }
}
