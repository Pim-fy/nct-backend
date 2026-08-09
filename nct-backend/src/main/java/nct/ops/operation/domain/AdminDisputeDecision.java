package nct.ops.operation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 담당자 7 · F-OPS-006: 현재 정책으로 자동 처리 가능한 관리자 분쟁 판정입니다. */
@Getter
@RequiredArgsConstructor
public enum AdminDisputeDecision {

    COMPLETE("TRDC0021", "TRDC0018", "처리 완료"),
    REFUND("TRDC0022", "TRDC0018", "전액 환불"),
    HOLD("TRDC0023", "TRDC0017", "정산 보류"),
    REJECT(null, "TRDC0019", "반려");

    private final String resultCode;
    private final String statusCode;
    private final String label;
}
