package nct.servicerequest.dto;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7: 요청자 수정 화면 전용 복호화 주소. 공개 상세 응답에는 세팅하지 않는다. */
@Getter
@Builder
public class ServiceRequestAddressItem {

    private String stepKey;
    private String addressFieldKey;
    private String detailFieldKey;
    private Integer sequence;
    private String address;
    private String detailAddress;
    private String zonecode;
    private String sido;
    private String sigungu;
}
