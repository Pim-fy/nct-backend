package nct.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [상품 - PRODUCT_VIEW_LOG 행 모델]
 * - 상품 조회수 중복 집계 방지용 방문 이력. 한 방문자(visitorKey)가 같은 상품을 24시간 내 재조회해도
 *   PRD_VIEW_CNT가 다시 늘지 않도록 판정하는 근거로 쓴다.
 * - visitorKey: 로그인 회원은 USR_SN 문자열, 비로그인 방문자는 서버가 발급한 익명 쿠키(UUID) 값
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewLog {

    private Long prdViewLogSn;
    private Long prdSn;
    private String visitorKey;
}
