package nct.ops.sanction.domain;

/** 담당자 7 · 신고 처리 제재: 7일 이용정지의 자동 또는 관리자 조기 해제 명령입니다. */
public record ReportSanctionReleaseCommand(
        Long sanctionSn,
        Long adminUserSn,
        String reason,
        String requestId,
        boolean automatic) {
}
