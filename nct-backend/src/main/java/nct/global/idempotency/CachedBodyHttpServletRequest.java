package nct.global.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.util.StreamUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

// @ai_generated: [반복읽기 가능한 요청 바디 래퍼] (F-COM-017)
// - 원본 InputStream은 1회만 읽을 수 있어, IdempotencyInterceptor가 해시 계산을 위해 먼저 읽어버리면
//   컨트롤러의 @RequestBody 역직렬화가 빈 스트림을 받아 실패한다.
// - 생성자에서 바디를 byte[]로 전부 읽어 캐싱하고, getInputStream() 호출마다 처음부터 다시 읽는
//   새 스트림을 반환해 인터셉터·컨트롤러가 각각 독립적으로 전체를 읽을 수 있게 한다.
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        CachedBodyServletInputStream(byte[] cachedBody) {
            this.buffer = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("비동기 서블릿 미사용 - 구현 불필요");
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }
}
