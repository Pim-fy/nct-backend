package nct.servicerequest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: 요청서 수정 복원을 위한 MyBatis 내부 구조화 답변 행. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestStoredAnswer {

    private String stepKey;
    private String fieldKey;
    private String stepOptionValue;
    private String stepOptionLabel;
    private String fieldOptionValue;
    private String fieldOptionLabel;
    private String value;
    private String encryptedValue;
    private String snapshot;
}
