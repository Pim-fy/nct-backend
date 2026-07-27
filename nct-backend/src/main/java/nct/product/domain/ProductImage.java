// Claude Code 작성 (BJN, 2026-07-17)
package nct.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [상품 - PRODUCT_IMAGE 행 모델] (담당자6, F-AUC-002 이미지 연계)
 * - 상품 하나에 여러 장(최대 5장)이 붙을 수 있고, 그중 한 장만 대표(prdImgRprsYn='Y')다.
 * - 등록 폼에서 넘어온 flSn 목록의 순서를 그대로 정렬순서(prdImgSortNo)로 쓰고,
 *   첫 번째 항목을 대표로 고정한다 (목업상 "대표이미지 1개 필수"와 동일한 규칙).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    private Long prdImgSn;
    private Long prdSn;
    private Long flSn;
    private String prdImgRprsYn;
    private Integer prdImgSortNo;
}
