package nct.global.logging;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * [MDC 유틸]
 * - MDC(Mapped Diagnostic Context) : 스레드 단위 로그 컨텍스트 저장소
 * - LoggingFilter 가 traceId/clientIp 를 넣어두면
 *   logback 패턴의 %X{traceId} 로 모든 로그 라인에 자동 출력됨
 */
public final class MdcUtils {

    public static final String TRACE_ID  = "traceId";
    public static final String CLIENT_IP = "clientIp";

    private MdcUtils() {}

    /** 요청 추적 ID 생성 (UUID 12자리) */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static void put(String key, String value) {
        MDC.put(key, value != null ? value : "-");
    }

    public static String get(String key) {
        String value = MDC.get(key);
        return value != null ? value : "-";
    }

    /** 요청 종료 시 반드시 호출 (스레드 풀 재사용으로 인한 오염 방지) */
    public static void clear() {
        MDC.clear();
    }
}
