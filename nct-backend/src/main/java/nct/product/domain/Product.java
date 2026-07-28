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

    // 임시저장(PRDC0001) 시점에 입력해둔 경매설정 보존값 — 실제 등록 전 AUCTION row가 없어 여기 보관.
    // 공개 등록(PRDC0002) 시 정식 AUCTION 값으로 이관되고 여기는 비워짐
    private BigDecimal prdDraftBidUnit;
    private LocalDateTime prdDraftStartDt;
    private LocalDateTime prdDraftEndDt;
    // 임시저장 시점에 "경매 정책을 확인하였습니다" 체크 여부(Y/N) — 재개 시 상품입력 탭 필수값이
    // 전부 채워져 있고 이 값도 Y면 등록확인 탭으로 바로 이동시키는 데 사용
    private String prdDraftPolicyAgreedYn;
}
