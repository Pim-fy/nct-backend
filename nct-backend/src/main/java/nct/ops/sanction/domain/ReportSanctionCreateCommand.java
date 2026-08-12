package nct.ops.sanction.domain;

import java.time.LocalDateTime;

/**
 * 담당자 7 · 신고 처리 제재: 신고 판정에서 생성할 기간제 또는 영구 계정 정지 명령입니다.
 */
public record ReportSanctionCreateCommand(
        Long reportSn,
        Long userSn,
        Long adminUserSn,
        String reason,
        LocalDateTime endAt,
        String requestId) {
}
