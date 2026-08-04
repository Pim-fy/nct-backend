package nct.servicerequest.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequest {

    private Long svcReqSn;         // 서비스요청일련번호 (PK)
    private Long usrSn;            // 요청자회원일련번호 (FK→USERS)
    private Long catSn;            // 카테고리일련번호 (FK→CATEGORY)
    private Long formTemplateSn;    // 작성 당시 서비스 요청 폼 템플릿
    private String svcReqTtl;      // 요청제목
    private String svcReqCn;       // 요청내용
    private BigDecimal svcReqBdgtAmt; // 예산금액
    private String svcReqStatusCd; // 요청상태공통코드 (SVCG01)
    private Character svcReqUseYn;
    private LocalDateTime svcReqRegDt;
    private LocalDateTime svcReqUpdtDt;
    private String svcReqRegId;
    private String svcReqUpdtId;
}
