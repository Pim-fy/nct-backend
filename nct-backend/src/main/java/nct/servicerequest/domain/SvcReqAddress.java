package nct.servicerequest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 7: F-SVC-002 SVC_REQ_ADDRESS 암호화 주소 행 모델. */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SvcReqAddress {

    private Long svcReqSn;
    private Long formTemplateSn;
    private Long stepSn;
    private String stepKey;
    private String addressRoleKey;
    private Integer sequence;
    private Long addressFieldSn;
    private String addressFieldKey;
    private Long detailFieldSn;
    private String detailFieldKey;
    private String sido;
    private String sigungu;
    private String encryptedAddress;
    private String encryptedDetailAddress;
    private String encryptedZonecode;
    private String publicRegionYn;
    private String regId;
    private String updtId;
}
