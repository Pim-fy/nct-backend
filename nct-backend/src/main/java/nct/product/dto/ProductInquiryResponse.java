package nct.product.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ProductInquiryResponse {

    private Long prdCmtSn;
    private Long usrSn;
    private String usrNm;
    private String prdCmtTypeCd;
    private Long prdCmtParentSn;
    private String prdCmtCn;
    private LocalDateTime prdCmtRegDt;
}
