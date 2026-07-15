package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [F-AUC-018 즉시구매 실행 결과 DTO]
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyNowResult {

    private Long aucSn;
    private Long buyerUsrSn;
    private Long finalAmt;                    // 실제 결제된 금액 (= PRODUCT.PRD_IBY_AMT)
    private Long tradeSn;                     // 담당자4 계약을 통해 생성된 거래 번호
    private Long refundedBidderUsrSn;         // 즉시구매로 인해 밀려나 환불된 기존 최고 입찰자 (없으면 null)
}
