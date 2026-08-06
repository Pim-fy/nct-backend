package nct.servicerequest.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class SvcReqCommentResponse {

    private Long svcReqCmtSn;
    private String svcReqCmtTtl;
    private String svcReqCmtCn;
    private LocalDateTime svcReqCmtRegDt;
}
