package nct.servicerequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 단계/필드 고정키 기반 구조화 답변 요청. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestAnswerRequest {

    @NotBlank
    @Size(max = 100)
    private String stepKey;

    @Size(max = 100)
    private String fieldKey;

    @Size(max = 500)
    private String optionValue;

    @Size(max = 4000)
    private String value;

    @Size(max = 500)
    private String otherText;
}
