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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import nct.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * [전역 예외 처리]
 * - 모든 예외를 ApiResponse 표준 포맷으로 변환
 * - 처리 우선순위
 *   1) MethodArgumentNotValidException : @Valid 검증 실패 -> 필드별 오류 목록
 *   2) MethodArgumentTypeMismatchException : 숫자·열거형 등의 요청 타입 변환 실패
 *   3) AuthenticationException / AccessDeniedException : 보안 예외
 *   4) CustomException : 비즈니스 예외 (ErrorCode 기반)
 *   5) Exception : 그 외 전부 500 (내부 메시지는 로그에만, 응답에는 노출 안 함)
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // 예외 메시지와 오류 응답 path에 이메일·전화번호 등이 그대로 노출되지 않게 사용한다.
    private final SensitiveDataMasker sensitiveDataMasker;

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
                                     safePath(request), errors));
    }

    /** 숫자 등의 요청 파라미터·경로 변수 타입 변환 실패 -> 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.INVALID_TYPE_VALUE;
        return ResponseEntity.badRequest()
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request)));
    }

    /** 인증 실패 -> 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request), errorCode.name()));
    }

    /** 권한 없음 -> 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request), errorCode.name()));

    }

    /** 업로드 파일이 spring.servlet.multipart.max-file-size 를 초과 -> 400 (담당자6, 파일 도메인) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request)));
    }

    /** 매핑되는 핸들러/정적 리소스(첨부파일 등)가 없음 -> 404 (담당자6, 파일 도메인)
     *  이 처리가 없으면 맨 아래 Exception 핸들러가 잡아 500으로 응답돼 버린다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request), errorCode.name()));
    }

    /** 비즈니스 예외 -> ErrorCode 의 상태코드/메시지 그대로 */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustom(
            CustomException ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.status())
                             .body(ApiResponse.error(errorCode.code(), ex.getMessage(),
                                                    safePath(request), errorCode.name()));

    }

    /** 나머지 전부 -> 500 (내부 정보는 응답에 노출하지 않음) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        logException(ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.internalServerError()
                             .body(ApiResponse.error(errorCode.code(), errorCode.message(),
                                                     safePath(request), errorCode.name()));
                                                     
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
                      sensitiveDataMasker.maskText(ex.getMessage()));
        } else {
            log.error("\n[Exception] {} - message={}", ex.getClass().getSimpleName(),
                      sensitiveDataMasker.maskText(ex.getMessage()));
        }
    }

    private String safePath(HttpServletRequest request) {
        // URL 인코딩된 개인정보까지 디코딩한 뒤 가린 안전한 경로만 응답에 포함한다.
        return sensitiveDataMasker.maskUri(request.getRequestURI());
    }
}
