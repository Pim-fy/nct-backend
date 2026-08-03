package nct.servicerequest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 정확주소 암호화 저장 요청. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestAddressRequest {

    @NotBlank
    @Size(max = 100)
    private String stepKey;

    @NotBlank
    @Size(max = 100)
    private String addressFieldKey;

    @Size(max = 100)
    private String detailFieldKey;

    @Min(1)
    private Integer sequence = 1;

    @NotBlank
    @Size(max = 500)
    private String address;

    @Size(max = 500)
    private String detailAddress;

    @Size(max = 20)
    private String zonecode;

    @Size(max = 50)
    private String sido;

    @Size(max = 100)
    private String sigungu;
}
