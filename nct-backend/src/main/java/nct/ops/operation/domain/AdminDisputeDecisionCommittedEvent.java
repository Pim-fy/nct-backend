package nct.ops.operation.domain;

import java.util.Set;

/** 담당자 7 · F-OPS-006: 커밋된 최종 분쟁 판정의 당사자 알림 정보입니다. */
public record AdminDisputeDecisionCommittedEvent(
        Set<Long> recipientUserSns,
        Long disputeSn,
        String resultText) {

    public AdminDisputeDecisionCommittedEvent {
        recipientUserSns = Set.copyOf(recipientUserSns);
    }
}
