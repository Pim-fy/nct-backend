package nct.trade.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-OPS-005: 거래 영역에 전달하는 관리자 분쟁 읽기 조건입니다. */
@Getter
@Setter
public class AdminTradeDisputeQuery {

    private Long searchNumber;
    private String disputeTypeCode;
    private String disputeStatusCode;
    private int page;
    private int size;

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
