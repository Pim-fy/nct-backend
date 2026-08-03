package nct.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 등록/조회에서 오가는 희망 거래지역 1건 — code=법정동코드, name=화면 표시용 지역명 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductTradeRegionItem {

    @NotBlank
    private String code;

    @NotBlank
    private String name;
}
