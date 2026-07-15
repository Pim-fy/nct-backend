package nct.auction.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [즉시구매 요청 DTO]
 * - BidRequest 와 달리 금액 필드가 없다. 즉시구매 가격은 클라이언트가 "제안"하는 값이 아니라
 *   서버가 PRODUCT.PRD_IBY_AMT 를 그대로 신뢰 기준으로 사용하기 때문이다.
 * - 클라이언트가 화면에 표시된 가격과 실제 서버 가격이 (이론상) 다를 일이 없어야 정상이지만,
 *   혹시라도 화면 캐시가 오래돼 다른 값을 볼 수도 있으므로, 애초에 금액을 받지 않는 설계로
 *   "클라이언트가 보낸 금액과 서버 금액이 다르면 어떻게 하나"라는 문제 자체를 없앴다.
 */
@Getter
@Setter
@NoArgsConstructor
public class BuyNowRequest {

    @NotNull(message = "경매 번호는 필수입니다.")
    private Long aucSn;
}
