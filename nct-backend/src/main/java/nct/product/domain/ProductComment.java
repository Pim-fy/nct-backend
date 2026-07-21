package nct.product.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductComment {

    private Long prdCmtSn;
    private Long prdSn;
    private Long usrSn;
    private String prdCmtTtl;
    private String prdCmtCn;
    private LocalDateTime prdCmtRegDt;
    private String prdCmtRegId;
    private LocalDateTime prdCmtUpdtDt;
    private String prdCmtUpdtId;
}
