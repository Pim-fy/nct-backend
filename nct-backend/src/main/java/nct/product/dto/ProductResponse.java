package nct.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long prdSn;
    private Long usrSn;
    private Long catSn;
    private String catNm;
    private String prdNm;
    private String prdCn;
    private String prdStatusCd;
    private BigDecimal prdStartAmt;
    private BigDecimal prdIbyAmt;
    private String prdTrdMethodCd;
    private LocalDateTime prdRegDt;
    private LocalDateTime prdUpdtDt;

    // 대표이미지 URL (없으면 null — 화면에서 기본 placeholder 처리). 담당자6, F-AUC-002 이미지 연계
    private String prdImgUrl;

    // F-AUC-005 경매 상태 연계 — 옥동민(5) AuctionService 일괄 조회로 세팅
    @Setter private Long aucSn;
    @Setter private String aucStatusCd;

    // F-AUC-005 거래 상태 연계 — 정민재(4) TradeService 일괄 조회로 세팅
    @Setter private Long tradeSn;
    @Setter private String tradeStatusCd;
}
