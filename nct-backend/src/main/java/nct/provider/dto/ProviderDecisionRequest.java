package nct.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-PROV-003/014: 반려·보완 사유는 반드시 남긴다는 관리자 심사 요청값입니다. */
@Getter @Setter
public class ProviderDecisionRequest {
    @NotBlank @Size(max = 4000)
    private String reason;
}
