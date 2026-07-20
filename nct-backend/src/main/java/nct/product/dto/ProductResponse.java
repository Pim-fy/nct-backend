package nct.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
}
