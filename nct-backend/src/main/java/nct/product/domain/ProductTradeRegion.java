package nct.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [상품 - PRODUCT_TRADE_REGION 행 모델]
 * - 직거래(TRDC0010)·둘 다 가능(TRDC0020) 상품에서 판매자가 희망하는 거래 지역 여러 곳(최대 5곳)을 저장한다.
 * - RGN_CD는 CMM_CODE가 아닌 법정동코드(시도 2자리 또는 시군구 5자리) — koreaRegions.js와 동일한 값을 그대로 저장한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTradeRegion {

    private Long prdTrdRgnSn;
    private Long prdSn;
    private String rgnCd;
    private String rgnNm;
}
