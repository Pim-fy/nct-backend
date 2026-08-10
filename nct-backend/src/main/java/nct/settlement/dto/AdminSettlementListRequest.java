package nct.settlement.dto;

import lombok.Getter;
import lombok.Setter;

/** F-OPS-009 관리자 정산 목록 필터입니다. */
@Getter
@Setter
public class AdminSettlementListRequest {

    private String statusCode;
    private String keyword;
    private int page = 1;
    private int size = 20;
}
