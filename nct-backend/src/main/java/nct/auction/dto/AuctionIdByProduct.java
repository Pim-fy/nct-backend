package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): findAuctionIdsByProductIds 배치 조회 결과
// 한 행. REVIEW·TRADE 도메인이 PRD_SN -> AUC_SN을 Mapper에서 직접 JOIN하지 않고 이 계약으로 받는다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuctionIdByProduct {
    private Long productId;
    private Long auctionId;
}
