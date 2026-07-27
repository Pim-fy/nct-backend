package nct.product.dto;

import lombok.Getter;

@Getter
public class InquiryReportTarget {

    private Long prdCmtSn;
    private Long prdSn;
    private Long writerUsrSn;
    private Long sellerUsrSn;
    private String prdCmtTypeCd;
}
