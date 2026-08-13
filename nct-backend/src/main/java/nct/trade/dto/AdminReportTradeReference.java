package nct.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-007: 자동 신고의 거래 번호를 원본 상품 또는 서비스 요청에 연결합니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminReportTradeReference {

    private Long tradeSn;
    private Long productSn;
    private Long serviceRequestSn;
}
