package nct.servicerequest.dto;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7: 요청자 수정 화면에 돌려주는 복원 가능한 구조화 답변. */
@Getter
@Builder
public class ServiceRequestAnswerItem {

    private String stepKey;
    private String fieldKey;
    private String optionValue;
    private String value;
    private String otherText;
}
