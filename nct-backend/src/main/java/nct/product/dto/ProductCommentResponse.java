package nct.product.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ProductCommentResponse {

    private Long prdCmtSn;
    private String prdCmtTtl;
    private String prdCmtCn;
    private LocalDateTime prdCmtRegDt;
    private LocalDateTime prdCmtUpdtDt;
}
