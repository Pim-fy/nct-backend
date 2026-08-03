package nct.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 조회수 증가 응답 — 옥동민(5) 경매 상세 캐시 반영용 계약 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewResponse {

    /** 이번 요청으로 실제로 조회수가 증가했는지 (24시간 내 중복 방문이면 false) */
    private boolean counted;

    /** 증가 반영 후 최종 조회수 */
    private long viewCount;
}
