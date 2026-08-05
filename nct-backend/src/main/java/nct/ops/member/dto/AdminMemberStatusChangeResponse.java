package nct.ops.member.dto;

/** 담당자 7 · F-OPS-019/020: 상태 변경과 거래·정산 보류 결과입니다. */
public record AdminMemberStatusChangeResponse(
        Long userSn,
        String previousStatusCode,
        String currentStatusCode,
        boolean changed,
        int restrictedTradeCount,
        int heldSettlementCount) {
}
