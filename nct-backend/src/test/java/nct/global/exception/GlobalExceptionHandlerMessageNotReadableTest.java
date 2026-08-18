package nct.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import nct.global.response.ApiResponse;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * DEF-3-002 / DEF-3-003 회귀 테스트 (황성경, 2026-08-16 발견) — 요청 본문이 문자열·배열 등
 * 형식이 아예 달라 객체로 역직렬화할 수 없을 때 500이 아니라 400을 응답해야 한다.
 */
class GlobalExceptionHandlerMessageNotReadableTest {

    @Test
    void respondsWith400WhenRequestBodyCannotBeDeserialized() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));

        GlobalExceptionHandler handler = new GlobalExceptionHandler(new SensitiveDataMasker());
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMessageNotReadable(exception, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getHttpCode()).isEqualTo(400);
    }
}
