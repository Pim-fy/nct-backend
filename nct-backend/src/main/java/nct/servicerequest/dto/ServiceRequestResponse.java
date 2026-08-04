package nct.servicerequest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequestResponse {

    private Long svcReqSn;
    private Long usrSn;
    private Long catSn;
    private Long formTemplateSn;
    private String catNm;
    private String svcReqTtl;
    private String svcReqCn;
    private BigDecimal svcReqBdgtAmt;
    private String svcReqStatusCd;
    private LocalDateTime svcReqRegDt;
    private LocalDateTime svcReqUpdtDt;
    private String thumbnailUrl;

    // 요청 항목 목록 — 상세 조회 시에만 세팅. 목록 조회(me)에서는 null
    @Setter private List<String> items;

    // 첨부사진 목록 — 상세 조회 시에만 세팅. 목록 조회(me)에서는 null
    @Setter private List<SvcReqImageItem> imageList;

    // 요청자 수정 화면에서만 세팅한다. 공개 상세에서는 null이다.
    @Setter private List<ServiceRequestAnswerItem> structuredAnswers;
    @Setter private List<ServiceRequestAddressItem> addressList;
}
