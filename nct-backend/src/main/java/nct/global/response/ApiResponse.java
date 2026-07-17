package nct.global.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/**
 * [표준 API 응답]
 * - 모든 응답을 동일한 구조로 통일
 *   : { timestamp, status, httpCode, message, path, data }
 * - @JsonInclude(NON_NULL) : null 필드는 JSON 에서 제외
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 서버 응답 시간 */
    private final LocalDateTime timestamp;

    /** 응답 상태 (success / error) */
    private final String status;

    /** HTTP 상태 코드 */
    private final int httpCode;

    /** 응답 메시지 */
    private final String message;

    /** 요청 주소 (에러 응답에만 포함) */
    private final String path;

    // @ai_generated: 안정적 에러 식별자(ErrorCode enum 이름, 예: "ACCOUNT_SUSPENDED").
    //   message는 문구가 바뀔 수 있어 프론트가 분기 조건으로 쓰기에 취약하므로, 코드값을 별도 제공한다.
    /** 에러 코드 (성공 응답에는 포함하지 않음) */
    private final String code;

    /** 응답 데이터 */
    private final T data;

    private ApiResponse(String status, int httpCode, String message, String path, String code, T data) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.httpCode = httpCode;
        this.message = message;
        this.path = path;
        this.code = code;
        this.data = data;
    }

    /*===========================
     * 성공 응답
     *===========================*/

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("success", 200, "ok", null, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", 200, "ok", null, null, data);
    }

    public static <T> ApiResponse<T> created() {
        return new ApiResponse<>("success", 201, "created", null, null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("success", 201, "created", null, null, data);
    }

    /*===========================
     * 실패 응답
     *===========================*/

    /**
     * 사용 예: ApiResponse.error(errorCode.code(), errorCode.message(), requestURI)
     * code(에러 식별자) 없이 호출하면 프론트가 상태값으로만 분기해야 하는 레거시 경로용이다.
     */
    public static <T> ApiResponse<T> error(int httpCode, String message, String path) {
        return new ApiResponse<>("error", httpCode, message, path, null, null);
    }

    // @ai_generated: 사용 예: ApiResponse.error(errorCode.code(), errorCode.message(), requestURI, errorCode.name())
    /** 안정적 code(ErrorCode enum 이름)를 포함한 실패 응답 */
    public static <T> ApiResponse<T> error(int httpCode, String message, String path, String code) {
        return new ApiResponse<>("error", httpCode, message, path, code, null);
    }

    /** 검증 오류처럼 상세 데이터가 필요한 실패 응답 */
    public static <T> ApiResponse<T> errorWithData(int httpCode, String message, String path, T data) {
        return new ApiResponse<>("error", httpCode, message, path, null, data);
    }
}
