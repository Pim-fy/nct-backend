package nct.global.exception;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import nct.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * [전역 예외 처리]
 * - 모든 예외를 ApiResponse 표준 포맷으로 변환
 * - 처리 우선순위
 *   1) MethodArgumentNotValidException : @Valid 검증 실패 -> 필드별 오류 목록
 *   2) AuthenticationException / AccessDeniedException : 보안 예외
 *   3) CustomException : 비즈니스 예외 (ErrorCode 기반)
 *   4) Exception : 그 외 전부 500 (내부 메시지는 로그에만, 응답에는 노출 안 함)
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 -> 400 + 필드별 오류 목록 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        logException(ex);

        List<Map<String, String>> errors =
                ex.getBindingResult().getFieldErrors().stream()
                  .map(fieldError -> Map.of(
                          "field",   fieldError.getField(),
                          "message", String.valueOf(fieldError.getDefaultMessage())))
                  .collect(Collectors.toList());

        return ResponseEntity.badRequest()
                             .body(ApiResponse.errorWithData(
                                     400, "입력값이 유효하지 않습니다.",
                                     request.getRequestURI(), errors));
    }

    /** 인증 실패 -> 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     request.getRequestURI()));
    }

    /** 권한 없음 -> 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     request.getRequestURI()));
    }

    /** 비즈니스 예외 -> ErrorCode 의 상태코드/메시지 그대로 */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustom(
            CustomException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), ex.getMessage(),
                                                     request.getRequestURI()));
    }

    /** 나머지 전부 -> 500 (내부 정보는 응답에 노출하지 않음) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.internalServerError()
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     request.getRequestURI()));
    }

    /** 예외 발생 지점(클래스/메서드/라인)을 포함한 에러 로그 */
    private void logException(Exception ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length > 0) {
            StackTraceElement origin = stackTrace[0];
            log.error("\n[Exception] {} at {}.{} ({}:{}) - message={}",
                      ex.getClass().getSimpleName(),
                      origin.getClassName(),
                      origin.getMethodName(),
                      origin.getFileName(),
                      origin.getLineNumber(),
                      ex.getMessage());
        } else {
            log.error("\n[Exception] {} - message={}", ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
