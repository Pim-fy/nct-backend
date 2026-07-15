package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [입찰 검증에 필요한 "상품" 정보만 뽑아낸 DTO]
 * - PRODUCT 테이블은 담당자2 소유이므로, 담당자3은 PRODUCT 테이블에 직접 Mapper 를
 *   만들어 조회하지 않는다. 대신 담당자2가 ProductQueryPort 를 통해 이 DTO 형태로만
 *   데이터를 내려준다 (경계: 7.1절 "직접 변경/조회 금지").
 * - 필드는 F-AUC-013 검증에 실제로 필요한 것만 최소로 뽑았다.
 *   (상품명, 설명 등은 입찰 검증과 무관하므로 여기 넣지 않는다 - 필요한 것만 계약에 포함)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBidInfo {

    /** 상품 등록자(판매자) 회원 번호 - 자기입찰 차단 검증(F-AUC-013 규칙)에 사용 */
    private Long sellerUsrSn;

    /**
     * 즉시구매금액 (PRODUCT.PRD_IBY_AMT)
     * - DDL 상 NULL 허용 컬럼이다 (즉시구매가를 등록하지 않은 상품도 있음).
     * - null 이면 "즉시구매가 제한 없음" 으로 해석해서 4번 규칙(즉시구매가 미만 검증)을 건너뛴다.
     */
    private Long buyNowAmt;
}
