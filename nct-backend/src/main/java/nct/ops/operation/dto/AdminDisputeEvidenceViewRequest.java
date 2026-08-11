package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-005/014: 분쟁 증빙 원문 열람 사유를 받습니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminDisputeEvidenceViewRequest {

    @NotBlank(message = "증빙 원문 열람 사유는 필수입니다.")
    @Size(max = 1000, message = "증빙 원문 열람 사유는 1,000자 이하여야 합니다.")
    private String reason;
}
