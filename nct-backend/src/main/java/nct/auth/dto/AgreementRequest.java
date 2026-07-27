package nct.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** 가입 시 저장할 고정 약관 1건의 동의 결과다. */
@Getter
@Setter
public class AgreementRequest {

    @NotBlank(message = "동의 유형은 필수입니다.")
    private String agreementTypeCode;

    @NotNull(message = "동의 여부는 필수입니다.")
    private Boolean agreed;
}
