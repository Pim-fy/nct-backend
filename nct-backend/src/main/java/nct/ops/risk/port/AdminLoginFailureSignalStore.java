package nct.ops.risk.port;

import java.time.LocalDateTime;

/** 담당자 7 · REQ-OPS-011: 감사 소유 영역이 제공하는 관리자 로그인 실패 기록 계약입니다. */
public interface AdminLoginFailureSignalStore {

    void record(String identityToken, String ipToken);

    long countSince(String tokenField, String tokenValue, LocalDateTime since);
}
