package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [F-AUC-013 검증 통과 결과 DTO]
 * - "검증을 통과했다"는 사실 그 자체를 담는 객체.
 * - F-AUC-014(입찰 실행)가 이 결과를 그대로 받아 BID 테이블에 INSERT 한다.
 *   즉, 013 -> 014 로 넘어갈 때의 "인수인계 데이터 형태"가 바로 이 클래스다.
 * - 이 객체가 만들어졌다는 것 자체가 "모든 검증 규칙을 통과했다"는 보증이 되도록
 *   BidService.validateBid() 내부에서만 생성하고, 다른 곳에서 임의로 new 하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidValidationResult {

    private Long aucSn;         // 검증을 통과한 경매 번호
    private Long bidderUsrSn;   // 입찰자 회원 번호
    private Long bidAmt;        // 검증을 통과한 입찰 금액
    private Long nextMinAmt;    // 참고용 - 검증 시점 기준 "다음" 최소 입찰가 (현재금액 + 입찰단위)
}
