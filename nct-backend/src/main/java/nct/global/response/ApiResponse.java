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

    /** 응답 데이터 */
    private final T data;

    private ApiResponse(String status, int httpCode, String message, String path, T data) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.httpCode = httpCode;
        this.message = message;
        this.path = path;
        this.data = data;
    }

    /*===========================
     * 성공 응답
     *===========================*/

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("success", 200, "ok", null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", 200, "ok", null, data);
    }

    public static <T> ApiResponse<T> created() {
        return new ApiResponse<>("success", 201, "created", null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("success", 201, "created", null, data);
    }

    /*===========================
     * 실패 응답
     *===========================*/

    /**
     * 사용 예: ApiResponse.error(errorCode.code(), errorCode.message(), requestURI)
     */
    public static <T> ApiResponse<T> error(int httpCode, String message, String path) {
        return new ApiResponse<>("error", httpCode, message, path, null);
    }

    /** 검증 오류처럼 상세 데이터가 필요한 실패 응답 */
    public static <T> ApiResponse<T> errorWithData(int httpCode, String message, String path, T data) {
        return new ApiResponse<>("error", httpCode, message, path, data);
    }
}
