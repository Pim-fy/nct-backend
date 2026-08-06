package nct.servicerequest.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SvcReqComment {

    private Long svcReqCmtSn;
    private Long svcReqSn;
    private Long usrSn;
    private String svcReqCmtTtl;
    private String svcReqCmtCn;
    private LocalDateTime svcReqCmtRegDt;
    private String svcReqCmtRegId;
}
