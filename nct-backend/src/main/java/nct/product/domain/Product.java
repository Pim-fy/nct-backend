package nct.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long prdSn;           // 상품일련번호 (PK)
    private Long usrSn;           // 판매자회원일련번호 (FK→USERS)
    private Long catSn;           // 카테고리일련번호 (FK→CATEGORY)
    private String prdNm;         // 상품명
    private String prdCn;         // 상품내용
    private String prdStatusCd;   // 상품상태공통코드 (PRDG01)
    private BigDecimal prdStartAmt; // 시작금액
    private BigDecimal prdIbyAmt;   // 즉시구매금액 (NULL 허용)
    private String prdTrdMethodCd;  // 거래방식공통코드 (TRDG03)
    private Character prdUseYn;     // 사용여부
    private LocalDateTime prdRegDt;
    private LocalDateTime prdUpdtDt;
    private String prdRegId;
    private String prdUpdtId;
}
