package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [F-AUC-014 입찰 실행 결과 DTO]
 * - 컨트롤러가 클라이언트에게 응답으로 돌려줄 최종 결과.
 * - previousHighestBidderUsrSn 이 null 이 아니면 "이번 입찰로 인해 밀려나 환불된 이전 최고 입찰자가 있다"는 뜻
 *   (F-AUC-016 이 실행되었음을 클라이언트/로그에서 확인할 수 있는 힌트).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidExecutionResult {

    private Long bidSn;                     // 새로 생성된 입찰 번호
    private Long aucSn;                     // 경매 번호
    private Long bidderUsrSn;               // 입찰자 번호
    private Long bidAmt;                    // 입찰 금액 (= 경매의 새로운 현재금액)
    private Long previousHighestBidderUsrSn; // 밀려난 이전 최고 입찰자 (없었으면 null - 이 경매의 첫 입찰)
}
