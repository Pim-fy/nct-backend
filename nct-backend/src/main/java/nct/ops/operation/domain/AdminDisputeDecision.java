package nct.ops.operation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 담당자 7 · F-OPS-006: 현재 정책으로 자동 처리 가능한 관리자 분쟁 판정입니다. */
@Getter
@RequiredArgsConstructor
public enum AdminDisputeDecision {

    COMPLETE("TRDC0011", "ABSC0003", "처리 완료"),
    REFUND("TRDC0012", "ABSC0003", "전액 환불"),
    HOLD("TRDC0013", "ABSC0002", "정산 보류"),
    REJECT(null, "ABSC0004", "반려");

    private final String resultCode;
    private final String statusCode;
    private final String label;
}
