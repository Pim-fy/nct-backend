package nct.ops.operation.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-OPS-005: 관리자 거래 분쟁 목록 검색 요청입니다. */
@Getter
@Setter
public class AdminDisputeListRequest {

    private String keyword;
    private String disputeTypeCode;
    private String disputeStatusCode;
    private int page = 1;
    private int size = 20;
}
