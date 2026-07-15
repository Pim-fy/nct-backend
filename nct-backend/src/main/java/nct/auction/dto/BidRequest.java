package nct.auction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [입찰 요청 DTO]
 * - 클라이언트(프론트) -> 컨트롤러로 들어오는 입력값
 * - @NotNull, @Positive 는 컨트롤러의 @Valid 와 함께 동작하는 "형식" 검증이다.
 *   (F-AUC-013 의 "경매 상태/즉시구매가/입찰단위" 같은 "비즈니스 규칙" 검증과는 층이 다르다.
 *    형식 검증은 DTO 에서, 비즈니스 규칙 검증은 BidService 에서 각각 담당한다.)
 */
@Getter
@Setter
@NoArgsConstructor
public class BidRequest {

    /**
     * 입찰 대상 경매 일련번호
     * - Controller 에서 @PathVariable 로 받은 값을 이 필드에 다시 세팅해서 쓰기도 한다.
     *   (URL 에 있는 경매 ID와 본문(body)의 값이 다르면 안 되므로 URL 값을 신뢰하는 편이 안전하다)
     */
    @NotNull(message = "경매 번호는 필수입니다.")
    private Long aucSn;

    /** 입찰 금액 - 0 이하 금액은 애초에 의미가 없으므로 형식 단계에서 걸러낸다. */
    @NotNull(message = "입찰 금액은 필수입니다.")
    @Positive(message = "입찰 금액은 0보다 커야 합니다.")
    private Long bidAmt;
}
