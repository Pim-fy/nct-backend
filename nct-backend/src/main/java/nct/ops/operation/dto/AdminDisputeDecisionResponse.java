package nct.ops.operation.dto;

import lombok.Builder;

/** 담당자 7 · F-OPS-006: 멱등 판정 여부와 최종 상태·환불액을 반환합니다. */
@Builder
public record AdminDisputeDecisionResponse(
        Long disputeSn,
        Long tradeSn,
        String decision,
        String disputeStatusCode,
        String disputeResultCode,
        boolean changed,
        long refundedAmount) {
}
